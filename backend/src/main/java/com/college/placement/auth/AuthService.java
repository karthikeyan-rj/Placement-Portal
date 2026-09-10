package com.college.placement.auth;

import com.college.placement.auth.dto.ChangePasswordRequest;
import com.college.placement.auth.dto.RegisterRequest;
import com.college.placement.auth.dto.RegisterResponse;
import com.college.placement.accesscode.AccessCodeHasher;
import com.college.placement.audit.AuditService;
import com.college.placement.security.SecurityUtils;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentCodeResolver;
import com.college.placement.student.StudentAccessCode;
import com.college.placement.student.StudentAccessCodeRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** Number of trailing numeric digits used to match short-form register numbers. */
    private static final int REGISTER_NUMBER_TAIL = 5;

    private static final String TCE_DOMAIN = "tce.edu";
    private static final String TCE_DOMAIN_SUFFIX = ".tce.edu";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentAccessCodeRepository accessCodeRepository;
    private final DepartmentCodeResolver departmentResolver;
    private final AccessCodeHasher accessCodeHasher;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @Transactional
    public RegisterResponse registerStudent(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String enteredRegisterNumber = request.getRegisterNumber().trim();
        String rawAccessCode = request.getAccessCode().trim();

        // 1. Only TCE email domains are allowed: name@tce.edu or name@<sub>.tce.edu.
        //    Anything like name@tce.edu.fake.com is rejected.
        if (!isAllowedTceEmail(email)) {
            throw new BadRequestException("This email is not authorized for registration.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account already exists for this email.");
        }
        if (studentProfileRepository.existsByRegisterNumber(enteredRegisterNumber)) {
            throw new ConflictException("An account already exists for this register number.");
        }

        // 2. Locate the authorized-student row by matching the trailing numeric digits
        //    of the register number (a short form is accepted, e.g. 24C21031 vs 2403917710421031).
        String enteredTail = lastFiveNumericDigits(enteredRegisterNumber);
        if (enteredTail.isEmpty()) {
            throw new BadRequestException("Register number does not match the student record.");
        }
        List<StudentAccessCode> byRegisterNumber = accessCodeRepository
                .findByRegisterNumberEndingWith(enteredTail).stream()
                .filter(c -> lastFiveNumericDigits(c.getRegisterNumber()).equals(enteredTail))
                .toList();
        if (byRegisterNumber.isEmpty()) {
            throw new BadRequestException("Register number does not match the student record.");
        }

        // 3. The submitted access code must belong to THIS exact student row (the same
        //    register number). It must never be validated globally, so a code belonging
        //    to a different student is simply rejected.
        StudentAccessCode accessCode = byRegisterNumber.stream()
                .filter(c -> accessCodeHasher.matches(rawAccessCode, c.getCodeHash()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Invalid access code for this student."));

        // 4. A code that was already consumed must not be reused.
        if (accessCode.getUsedAt() != null) {
            throw new BadRequestException("This access code has already been used.");
        }

        // 5. Everything identifies the same CSV-backed student row.
        Department department = departmentResolver.resolve(accessCode.getDepartmentCode())
                .orElseThrow(() -> new BadRequestException("Department unavailable."));
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new BadRequestException("Department unavailable.");
        }

        // Role and department always come from the CSV-backed authorization record,
        // never from the client. Authorized students are always STUDENTs.
        User user = User.builder()
                .name(accessCode.getName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.STUDENT)
                .department(department)
                .active(true)
                .build();
        user = userRepository.save(user);

        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .registerNumber(enteredRegisterNumber)
                .build();
        profile = studentProfileRepository.save(profile);

        accessCode.setStudentProfile(profile);
        accessCode.setEmail(email);
        accessCode.setUsedAt(LocalDateTime.now());
        accessCode.setActive(false);
        accessCodeRepository.save(accessCode);

        log.info("Student registered via access code: {} ({})", email, enteredRegisterNumber);

        return RegisterResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .departmentId(department.getId())
                .departmentName(department.getName())
                .build();
    }

    /**
     * Changes the password for the currently authenticated (JWT) user.
     * The user is resolved from the security context, never from a client-supplied id.
     * The current password must match the stored BCrypt hash; the new password is
     * BCrypt-hashed and persisted. Password values are never logged.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = securityUtils.getCurrentUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        String newPassword = request.getNewPassword().trim();
        if (newPassword.length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditService.log("CHANGE_PASSWORD", "User", user.getId(), user.getEmail());
    }

    /**
     * Accepts the TCE email domain and any of its subdomains, e.g.
     * name@tce.edu, name@student.tce.edu, name@staff.tce.edu.
     * Rejects lookalike domains such as name@tce.edu.fake.com.
     */
    private static boolean isAllowedTceEmail(String email) {
        if (email == null) return false;
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) return false;
        String local = email.substring(0, at);
        if (local.trim().isEmpty()) return false;
        String domain = email.substring(at + 1).toLowerCase();
        return TCE_DOMAIN.equals(domain) || domain.endsWith(TCE_DOMAIN_SUFFIX);
    }

    /**
     * Extracts the last {@code REGISTER_NUMBER_TAIL} numeric digits of a register
     * number, ignoring any non-digit characters. Example: "24C21031" → "21031" and
     * "2403917710421031" → "21031". Implemented generically (no department prefix
     * assumptions). Returns an empty string when there are no digits.
     */
    private static String lastFiveNumericDigits(String registerNumber) {
        if (registerNumber == null) return "";
        String digits = registerNumber.replaceAll("\\D", "");
        if (digits.length() <= REGISTER_NUMBER_TAIL) return digits;
        return digits.substring(digits.length() - REGISTER_NUMBER_TAIL);
    }
}
