package com.college.placement.report;

import com.college.placement.common.enums.PlacementDriveStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.company.CompanyRepository;
import com.college.placement.messaging.Message;
import com.college.placement.messaging.MessageRecipient;
import com.college.placement.messaging.MessageRecipientRepository;
import com.college.placement.messaging.MessageReaction;
import com.college.placement.messaging.MessageReactionRepository;
import com.college.placement.messaging.MessageRepository;
import com.college.placement.placement.PlacementDriveRepository;
import com.college.placement.placement.PlacementRecordRepository;
import com.college.placement.report.dto.ReportSummaryResponse;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentAcademic;
import com.college.placement.student.StudentAcademicRepository;
import com.college.placement.student.StudentPlacementInfo;
import com.college.placement.student.StudentPlacementInfoRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.opencsv.CSVWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository recipientRepository;
    private final MessageReactionRepository reactionRepository;
    private final PlacementRecordRepository placementRecordRepository;
    private final PlacementDriveRepository placementDriveRepository;
    private final CompanyRepository companyRepository;
    private final StudentProfileRepository profileRepository;
    private final StudentAcademicRepository academicRepository;
    private final StudentPlacementInfoRepository placementInfoRepository;
    private final SecurityUtils securityUtils;

    private static double rate(long placed, long total) {
        return total > 0 ? Math.round(placed * 1000.0 / total) / 10.0 : 0.0;
    }

    // Aggregate summary computed entirely in the database (F4): no full DTO
    // downloads. PO gets a global view; PC is forced into their own department.
    @Transactional(readOnly = true)
    public ReportSummaryResponse getSummary(Long departmentId) {
        Role currentRole = securityUtils.getCurrentUserRole();

        Long scopeDeptId = departmentId;
        if (currentRole == Role.PC) {
            Long ownDeptId = securityUtils.getCurrentUser().getDepartment() != null
                    ? securityUtils.getCurrentUser().getDepartment().getId() : null;
            if (ownDeptId == null) {
                throw new ForbiddenException("You are not assigned to a department.");
            }
            if (scopeDeptId != null && !scopeDeptId.equals(ownDeptId)) {
                throw new ForbiddenException("You can only view summaries for your own department.");
            }
            scopeDeptId = ownDeptId;
        } else if (currentRole != Role.PO) {
            throw new ForbiddenException("You do not have permission to view report summaries.");
        }

        long total = profileRepository.countActivePopulation(scopeDeptId);
        long interested = profileRepository.countPlacementInterested(scopeDeptId);

        long placed = 0;
        long blocked = 0;
        for (StudentProfileRepository.StatusCountProjection row : profileRepository.countByPlacementStatus(scopeDeptId)) {
            String status = row.getStatus();
            if ("PLACED".equals(status)) placed = row.getCount();
            else if ("BLOCKED".equals(status)) blocked = row.getCount();
        }
        long notPlaced = total - placed - blocked;

        long activeDrives = placementDriveRepository.countActive(
                List.of(PlacementDriveStatus.COMPLETED, PlacementDriveStatus.CANCELLED));
        long completedDrives = placementDriveRepository.countByStatus(PlacementDriveStatus.COMPLETED);
        long activeCompanies = companyRepository.countByActiveTrue();

        List<ReportSummaryResponse.DepartmentRow> byDepartment = new ArrayList<>();
        for (StudentProfileRepository.DepartmentAggregateProjection row
                : profileRepository.summarizeByDepartment(scopeDeptId)) {
            String name = row.getDepartmentName() != null
                    ? row.getDepartmentName() : "Department #" + row.getDepartmentId();
            byDepartment.add(ReportSummaryResponse.DepartmentRow.builder()
                    .departmentId(row.getDepartmentId())
                    .departmentName(name)
                    .studentCount(row.getStudentCount())
                    .interestedCount(row.getInterestedCount())
                    .placedCount(row.getPlacedCount())
                    .placementRate(rate(row.getPlacedCount(), row.getStudentCount()))
                    .build());
        }
        byDepartment.sort(Comparator
                .comparingLong(ReportSummaryResponse.DepartmentRow::getPlacedCount).reversed()
                .thenComparing(Comparator.comparingLong(ReportSummaryResponse.DepartmentRow::getStudentCount).reversed()));

        List<ReportSummaryResponse.BatchRow> byBatch = new ArrayList<>();
        for (StudentProfileRepository.BatchAggregateProjection row
                : profileRepository.summarizeByBatch(scopeDeptId)) {
            String batch = row.getBatch() != null && !row.getBatch().isBlank()
                    ? row.getBatch() : "Not specified";
            byBatch.add(ReportSummaryResponse.BatchRow.builder()
                    .batch(batch)
                    .studentCount(row.getStudentCount())
                    .interestedCount(row.getInterestedCount())
                    .placedCount(row.getPlacedCount())
                    .placementRate(rate(row.getPlacedCount(), row.getStudentCount()))
                    .build());
        }
        byBatch.sort(Comparator
                .comparing(ReportSummaryResponse.BatchRow::getBatch,
                        Comparator.comparing((String b) -> "Not specified".equals(b))
                                .thenComparing(Comparator.naturalOrder())));

        return ReportSummaryResponse.builder()
                .totalStudentPopulation(total)
                .placementInterested(interested)
                .placed(placed)
                .notPlaced(notPlaced)
                .blocked(blocked)
                .placementRate(rate(placed, total))
                .activeDrives(activeDrives)
                .completedDrives(completedDrives)
                .activeCompanies(activeCompanies)
                .byDepartment(byDepartment)
                .byBatch(byBatch)
                .build();
    }

    @Transactional(readOnly = true)
    public void exportMessageAcknowledgements(Long messageId, HttpServletResponse response) throws Exception {
        securityUtils.requireAnyRole(Role.PO, Role.PC);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        List<MessageRecipient> recipients =
                recipientRepository.findByMessageIdWithUserAndDepartment(messageId);

        if (securityUtils.isPC()) {
            Long currentDeptId = securityUtils.getCurrentUser().getDepartment() != null
                    ? securityUtils.getCurrentUser().getDepartment().getId() : null;
            boolean messageWithinDept = recipients.stream().allMatch(r ->
                    r.getRecipient().getDepartment() != null
                            && r.getRecipient().getDepartment().getId().equals(currentDeptId));
            if (!messageWithinDept) {
                throw new ForbiddenException("You can only export acknowledgements for messages within your department.");
            }
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=message_" + messageId + "_responses.csv");

        PrintWriter writer = response.getWriter();
        CSVWriter csvWriter = new CSVWriter(writer);

        csvWriter.writeNext(new String[]{
                "Register Number", "Name", "Department", "Delivery Status",
                "Read Status", "Reaction", "Timestamp"
        });

        List<Long> recipientUserIds = recipients.stream()
                .map(r -> r.getRecipient().getId())
                .toList();

        Map<Long, String> registerByUser = new HashMap<>();
        if (!recipientUserIds.isEmpty()) {
            List<StudentProfile> profiles = profileRepository.findWithUserByUserIds(recipientUserIds);
            for (StudentProfile p : profiles) {
                registerByUser.put(p.getUser().getId(), p.getRegisterNumber());
            }
        }

        Map<Long, String> reactionByUser = new HashMap<>();
        if (!recipientUserIds.isEmpty()) {
            for (MessageReaction r : reactionRepository.findByMessageId(messageId)) {
                reactionByUser.put(r.getUser().getId(), r.getReaction().name());
            }
        }

        for (MessageRecipient recipient : recipients) {
            String registerNumber = registerByUser.getOrDefault(recipient.getRecipient().getId(), "");
            String studentName = recipient.getRecipient().getName();
            String department = recipient.getRecipient().getDepartment() != null ?
                    recipient.getRecipient().getDepartment().getName() : "N/A";

            String deliveryStatus = recipient.getDeliveredAt() != null ? "DELIVERED" : "PENDING";
            String readStatus = recipient.getReadAt() != null ? "READ" : "UNREAD";

            String reaction = reactionByUser.getOrDefault(recipient.getRecipient().getId(), "NONE");

            String timestamp = recipient.getCreatedAt() != null ? recipient.getCreatedAt().toString() : "";

            csvWriter.writeNext(new String[]{
                    registerNumber, studentName, department,
                    deliveryStatus, readStatus, reaction, timestamp
            });
        }

        csvWriter.flush();
        csvWriter.close();
    }

    @Transactional(readOnly = true)
    public void exportStudentsCsv(Long departmentId, HttpServletResponse response) throws Exception {
        securityUtils.requireAnyRole(Role.PO, Role.PC);

        if (securityUtils.isPC()) {
            Long currentDeptId = securityUtils.getCurrentUser().getDepartment() != null
                    ? securityUtils.getCurrentUser().getDepartment().getId() : null;
            if (departmentId != null && !departmentId.equals(currentDeptId)) {
                throw new ForbiddenException("You can only export students from your own department.");
            }
            departmentId = currentDeptId;
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=students_export.csv");

        PrintWriter writer = response.getWriter();
        CSVWriter csvWriter = new CSVWriter(writer);

        csvWriter.writeNext(new String[]{
                "Register Number", "Name", "Email", "Department",
                "Batch", "Section", "CGPA", "Active Backlogs",
                "Placement Interested", "Placement Status"
        });

        List<StudentProfile> profiles;
        if (departmentId != null) {
            profiles = profileRepository.findWithDetailsByDepartmentId(departmentId);
        } else {
            profiles = profileRepository.findAllWithDetails();
        }

        List<Long> profileIds = profiles.stream().map(StudentProfile::getId).toList();

        Map<Long, StudentAcademic> academicByProfile = profileIds.isEmpty() ? Map.of() :
                academicRepository.findAllByStudentProfileIdIn(profileIds).stream()
                        .collect(Collectors.toMap(a -> a.getStudentProfile().getId(), Function.identity()));
        Map<Long, StudentPlacementInfo> placementByProfile = profileIds.isEmpty() ? Map.of() :
                placementInfoRepository.findAllByStudentProfileIdIn(profileIds).stream()
                        .collect(Collectors.toMap(p -> p.getStudentProfile().getId(), Function.identity()));

        for (var profile : profiles) {
            var academic = academicByProfile.get(profile.getId());
            var placementInfo = placementByProfile.get(profile.getId());

            String cgpa = academic != null && academic.getCgpa() != null ? academic.getCgpa().toPlainString() : "";
            String activeBacklogs = academic != null && academic.getActiveBacklogs() != null
                    ? academic.getActiveBacklogs().toString() : "";
            String interested = placementInfo != null && placementInfo.getPlacementInterested() != null
                    ? (placementInfo.getPlacementInterested() ? "YES" : "NO") : "";
            String status = placementInfo != null && placementInfo.getPlacementStatus() != null
                    ? placementInfo.getPlacementStatus().name() : "";

            csvWriter.writeNext(new String[]{
                    profile.getRegisterNumber(),
                    profile.getUser().getName(),
                    profile.getUser().getEmail(),
                    profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getName() : "N/A",
                    profile.getBatch() != null ? profile.getBatch() : "",
                    profile.getSection() != null ? profile.getSection() : "",
                    cgpa, activeBacklogs, interested, status
            });
        }

        csvWriter.flush();
        csvWriter.close();
    }

    @Transactional(readOnly = true)
    public void exportPlacementReport(HttpServletResponse response) throws Exception {
        securityUtils.requireRole(Role.PO);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=placement_report.csv");

        PrintWriter writer = response.getWriter();
        CSVWriter csvWriter = new CSVWriter(writer);

        csvWriter.writeNext(new String[]{
                "Register Number", "Student Name", "Department",
                "Company", "Package (LPA)", "Placement Date", "Status"
        });

        var allProfiles = profileRepository.findAllWithDetails();
        for (var profile : allProfiles) {
            var records = placementRecordRepository.findByStudentProfileIdOrderByPlacementDateDesc(profile.getId());
            for (var record : records) {
                csvWriter.writeNext(new String[]{
                        profile.getRegisterNumber(),
                        profile.getUser().getName(),
                        profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getName() : "N/A",
                        record.getCompany() != null ? record.getCompany().getName() : "N/A",
                        record.getPackageLpa() != null ? record.getPackageLpa().toPlainString() : "N/A",
                        record.getPlacementDate() != null ? record.getPlacementDate().toString() : "N/A",
                        record.getStatus()
                });
            }
        }

        csvWriter.flush();
        csvWriter.close();
    }
}
