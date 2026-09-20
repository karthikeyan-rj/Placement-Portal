package com.college.placement.user;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.department.PrConfigRepository;
import com.college.placement.security.SecurityUtils;
import com.college.placement.user.dto.CreateUserRequest;
import com.college.placement.user.dto.UpdateUserRoleRequest;
import com.college.placement.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PrConfigRepository prConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String search, Role role, Long departmentId, Pageable pageable) {
        Role currentRole = securityUtils.getCurrentUserRole();

        if (currentRole == Role.PO) {
            return userRepository.searchUsersScoped(departmentId, role != null ? role.name() : null,
                    search, pageable).map(this::toResponse);
        } else if (currentRole == Role.PC) {
            Long deptId = securityUtils.getCurrentUser().getDepartment().getId();
            if (departmentId != null && !departmentId.equals(deptId)) {
                throw new ForbiddenException("You can only search users within your department.");
            }
            return userRepository.searchUsersScoped(deptId, role != null ? role.name() : null,
                    search, pageable).map(this::toResponse);
        }

        throw new ForbiddenException("You do not have permission to search users.");
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        validateAccess(user);
        return toResponse(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        securityUtils.requireRole(Role.PO);

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }

        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + request.getRole());
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(true)
                .build();

        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            user.setDepartment(dept);
        }

        user = userRepository.save(user);
        auditService.log("CREATE_USER", "User", user.getId(),
                user.getEmail() + " [" + user.getRole() + "]");

        return toResponse(user);
    }

    @Transactional
    public UserResponse assignPcToDepartment(Long userId, Long departmentId) {
        securityUtils.requireRole(Role.PO);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));

        if (!dept.getActive()) {
            throw new BadRequestException("Cannot assign PC to inactive department.");
        }

        long pcCount = userRepository.countByRoleAndDepartment(Role.PC, departmentId);
        Long userDeptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        boolean noOpReassign = user.getRole() == Role.PC && departmentId.equals(userDeptId);
        if (pcCount >= 2 && !noOpReassign) {
            throw new BadRequestException("Maximum 2 PCs allowed per department.");
        }

        String oldDept = user.getDepartment() != null ? user.getDepartment().getName() : "None";
        user.setRole(Role.PC);
        user.setDepartment(dept);
        userRepository.save(user);

        auditService.log("ASSIGN_PC", "User", user.getId(),
                oldDept, dept.getName());

        return toResponse(user);
    }

    @Transactional
    public UserResponse promoteToPr(Long studentUserId) {
        Role currentRole = securityUtils.getCurrentUserRole();
        User currentUser = securityUtils.getCurrentUser();

        User student = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentUserId));

        if (student.getRole() != Role.STUDENT) {
            throw new BadRequestException("Only students can be promoted to PR.");
        }

        Department studentDept = student.getDepartment();
        if (studentDept == null) {
            throw new BadRequestException("Student must belong to a department.");
        }

        if (currentRole == Role.PC) {
            if (currentUser.getDepartment() == null ||
                !currentUser.getDepartment().getId().equals(studentDept.getId())) {
                throw new ForbiddenException("PC can only promote students within their assigned department.");
            }
        } else if (currentRole != Role.PO) {
            throw new ForbiddenException("Only PO or PC can promote students to PR.");
        }

        long currentPrCount = userRepository.countByRoleAndDepartment(Role.PR, studentDept.getId());
        Integer maxPrs = prConfigRepository.findByDepartmentId(studentDept.getId())
                .map(config -> config.getMaxPrs())
                .orElse(5);

        if (currentPrCount >= maxPrs) {
            throw new BadRequestException("PR limit reached for department: " + studentDept.getName()
                    + ". Maximum allowed: " + maxPrs);
        }

        String oldRole = student.getRole().name();
        student.setRole(Role.PR);
        userRepository.save(student);

        auditService.log("PROMOTE_TO_PR", "User", student.getId(),
                oldRole + " in " + studentDept.getName(),
                "PR in " + studentDept.getName());

        return toResponse(student);
    }

    @Transactional
    public UserResponse demoteToStudent(Long prUserId) {
        Role currentRole = securityUtils.getCurrentUserRole();
        User currentUser = securityUtils.getCurrentUser();

        User pr = userRepository.findById(prUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", prUserId));

        if (pr.getRole() != Role.PR) {
            throw new BadRequestException("Only PRs can be demoted to students.");
        }

        Department prDept = pr.getDepartment();
        if (prDept == null) {
            throw new BadRequestException("PR must belong to a department.");
        }

        if (currentRole == Role.PC) {
            if (currentUser.getDepartment() == null ||
                !currentUser.getDepartment().getId().equals(prDept.getId())) {
                throw new ForbiddenException("PC can only demote PRs within their assigned department.");
            }
        } else if (currentRole != Role.PO) {
            throw new ForbiddenException("Only PO or PC can demote PRs.");
        }

        String oldRole = pr.getRole().name();
        pr.setRole(Role.STUDENT);
        userRepository.save(pr);

        auditService.log("DEMOTE_TO_STUDENT", "User", pr.getId(),
                oldRole + " in " + prDept.getName(),
                "STUDENT in " + prDept.getName());

        return toResponse(pr);
    }

    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRoleAndDepartment(Role role, Long departmentId) {
        return userRepository.findByRoleAndDepartmentId(role, departmentId).stream()
                .map(this::toResponse)
                .toList();
    }

    // Canonical stats (F2): totalStudents = active users with a StudentProfile whose
    // role is STUDENT or PR (PRs are promoted students, not separate people).
    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        return Map.of(
                "totalStudents", userRepository.countActiveStudentPopulation(),
                "totalPcs", userRepository.countByRole(Role.PC),
                "totalPrs", userRepository.countByRole(Role.PR),
                "totalPOs", userRepository.countByRole(Role.PO)
        );
    }

    private void validateAccess(User targetUser) {
        Role currentRole = securityUtils.getCurrentUserRole();
        if (currentRole == Role.PO) return;

        User currentUser = securityUtils.getCurrentUser();
        if (currentRole == Role.PC || currentRole == Role.PR) {
            if (currentUser.getDepartment() == null ||
                targetUser.getDepartment() == null ||
                !currentUser.getDepartment().getId().equals(targetUser.getDepartment().getId())) {
                throw new ForbiddenException("You can only access users within your department.");
            }
        } else {
            throw new ForbiddenException("You do not have permission to access user details.");
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .active(user.getActive())
                .build();
    }
}
