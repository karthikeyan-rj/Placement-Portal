package com.college.placement.report;

import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.messaging.MessageRecipient;
import com.college.placement.messaging.MessageRecipientRepository;
import com.college.placement.messaging.MessageReaction;
import com.college.placement.messaging.MessageReactionRepository;
import com.college.placement.placement.PlacementRecordRepository;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentPlacementInfoRepository;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.UserRepository;
import com.college.placement.department.DepartmentRepository;
import com.opencsv.CSVWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final MessageRecipientRepository recipientRepository;
    private final MessageReactionRepository reactionRepository;
    private final PlacementRecordRepository placementRecordRepository;
    private final StudentProfileRepository profileRepository;
    private final StudentPlacementInfoRepository placementInfoRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final SecurityUtils securityUtils;

    public void exportMessageAcknowledgements(Long messageId, HttpServletResponse response) throws Exception {
        securityUtils.requireAnyRole(Role.PO, Role.PC);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=message_" + messageId + "_responses.csv");

        PrintWriter writer = response.getWriter();
        CSVWriter csvWriter = new CSVWriter(writer);

        csvWriter.writeNext(new String[]{
                "Register Number", "Name", "Department", "Delivery Status",
                "Read Status", "Reaction", "Timestamp"
        });

        List<MessageRecipient> recipients = recipientRepository.findByMessageId(messageId);

        for (MessageRecipient recipient : recipients) {
            String registerNumber = "";
            String studentName = recipient.getRecipient().getName();
            String department = recipient.getRecipient().getDepartment() != null ?
                    recipient.getRecipient().getDepartment().getName() : "N/A";

            var profile = com.college.placement.student.dto.StudentProfileResponse.builder().build();
            var studentProfiles = profileRepository.findByUserId(recipient.getRecipient().getId());
            if (studentProfiles.isPresent()) {
                registerNumber = studentProfiles.get().getRegisterNumber();
            }

            String deliveryStatus = recipient.getDeliveredAt() != null ? "DELIVERED" : "PENDING";
            String readStatus = recipient.getReadAt() != null ? "READ" : "UNREAD";

            String reaction = "NONE";
            var msgReaction = reactionRepository.findByMessageIdAndUserId(messageId, recipient.getRecipient().getId());
            if (msgReaction.isPresent()) {
                reaction = msgReaction.get().getReaction().name();
            }

            String timestamp = recipient.getCreatedAt() != null ? recipient.getCreatedAt().toString() : "";

            csvWriter.writeNext(new String[]{
                    registerNumber, studentName, department,
                    deliveryStatus, readStatus, reaction, timestamp
            });
        }

        csvWriter.flush();
        csvWriter.close();
    }

    public void exportStudentsCsv(Long departmentId, HttpServletResponse response) throws Exception {
        securityUtils.requireAnyRole(Role.PO, Role.PC);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=students_export.csv");

        PrintWriter writer = response.getWriter();
        CSVWriter csvWriter = new CSVWriter(writer);

        csvWriter.writeNext(new String[]{
                "Register Number", "Name", "Email", "Department",
                "Batch", "Section", "CGPA", "Active Backlogs",
                "Placement Interested", "Placement Status"
        });

        List<com.college.placement.student.StudentProfile> profiles;
        if (departmentId != null) {
            profiles = profileRepository.findByUserDepartmentId(departmentId);
        } else {
            profiles = profileRepository.findAll();
        }

        for (var profile : profiles) {
            var academic = new com.college.placement.student.StudentAcademic();
            var placementInfo = new com.college.placement.student.StudentPlacementInfo();

            csvWriter.writeNext(new String[]{
                    profile.getRegisterNumber(),
                    profile.getUser().getName(),
                    profile.getUser().getEmail(),
                    profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getName() : "N/A",
                    profile.getBatch() != null ? profile.getBatch() : "",
                    profile.getSection() != null ? profile.getSection() : "",
                    "", "", "", ""
            });
        }

        csvWriter.flush();
        csvWriter.close();
    }

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

        var allProfiles = profileRepository.findAll();
        for (var profile : allProfiles) {
            var records = placementRecordRepository.findByStudentProfileIdOrderByPlacementDateDesc(profile.getId());
            for (var record : records) {
                csvWriter.writeNext(new String[]{
                        profile.getRegisterNumber(),
                        profile.getUser().getName(),
                        profile.getUser().getDepartment() != null ? profile.getUser().getDepartment().getName() : "N/A",
                        record.getCompany().getName(),
                        record.getPackageLpa() != null ? record.getPackageLpa().toString() : "N/A",
                        record.getPlacementDate() != null ? record.getPlacementDate().toString() : "N/A",
                        record.getStatus()
                });
            }
        }

        csvWriter.flush();
        csvWriter.close();
    }
}
