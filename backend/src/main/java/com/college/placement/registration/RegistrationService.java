package com.college.placement.registration;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.RateLimitException;
import com.college.placement.common.validation.TceEmailValidator;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentCodeResolver;
import com.college.placement.messaging.email.EmailDeliveryService;
import com.college.placement.registration.dto.CompleteRegistrationRequest;
import com.college.placement.registration.dto.CompleteRegistrationResponse;
import com.college.placement.registration.dto.RequestCodeRequest;
import com.college.placement.registration.dto.RequestCodeResponse;
import com.college.placement.registration.dto.VerifyCodeRequest;
import com.college.placement.registration.dto.VerifyCodeResponse;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final int REGISTER_NUMBER_TAIL = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final StudentAccessCodeRepository accessCodeRepository;
    private final RegistrationVerificationCodeRepository verificationCodeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final DepartmentCodeResolver departmentResolver;
    private final RegistrationCodeGenerator codeGenerator;
    private final RegistrationTokenService tokenService;
    private final EmailDeliveryService emailDeliveryService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Step 1: Request a 6-digit one-time email verification code.
     * Validates that the student exists in the PO-imported roster and is not already registered.
     */
    @Transactional
    public RequestCodeResponse requestCode(RequestCodeRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String enteredRegisterNumber = request.getRegisterNumber().trim();

        // 1. Domain validation
        if (!TceEmailValidator.isAllowedTceEmail(email)) {
            throw new BadRequestException("This email is not authorized for registration.");
        }

        // 2. Check if already registered
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("This account is already registered. Sign in instead.");
        }
        if (studentProfileRepository.existsByRegisterNumber(enteredRegisterNumber)) {
            throw new ConflictException("This account is already registered. Sign in instead.");
        }

        // 3. Locate authorized student roster record
        StudentAccessCode sac = findAuthorizedStudent(email, enteredRegisterNumber);

        // 4. Ensure student has not already registered
        if (sac.getUsedAt() != null || Boolean.FALSE.equals(sac.getActive()) || sac.getStudentProfile() != null) {
            throw new ConflictException("This account is already registered. Sign in instead.");
        }

        LocalDateTime now = LocalDateTime.now();

        // 5. Enforce 60-second resend cooldown
        List<RegistrationVerificationCode> activeCodes = verificationCodeRepository.findActiveByAuthorizedStudentId(sac.getId(), now.minusSeconds(RESEND_COOLDOWN_SECONDS));
        if (!activeCodes.isEmpty() && activeCodes.get(0).getCreatedAt().isAfter(now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            throw new RateLimitException("Please wait 60 seconds before requesting another code.");
        }

        // 6. Enforce max 5 sends per email per hour
        long sendsThisHour = verificationCodeRepository.countByEmailSince(email, now.minusHours(1));
        if (sendsThisHour >= MAX_SENDS_PER_HOUR) {
            throw new RateLimitException("Too many verification code requests. Please try again in an hour.");
        }

        // 7. Invalidate previous active codes for this student
        verificationCodeRepository.invalidateAllForAuthorizedStudent(sac.getId(), now);

        // 8. Generate cryptographically secure 6-digit numeric code
        String code = codeGenerator.generate6DigitCode();

        // 9. Persist hashed code
        RegistrationVerificationCode rvc = RegistrationVerificationCode.builder()
                .authorizedStudent(sac)
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plusMinutes(10))
                .attemptCount(0)
                .createdAt(now)
                .build();
        verificationCodeRepository.save(rvc);

        // 10. Send verification email synchronously via Resend (never log code)
        emailDeliveryService.sendRegistrationCode(email, code);

        String maskedEmail = maskEmail(email);
        log.info("Verification code issued and sent to authorized student: {} ({})", maskedEmail, sac.getRegisterNumber());

        return RequestCodeResponse.builder()
                .message("We sent a verification code to " + maskedEmail)
                .maskedEmail(maskedEmail)
                .build();
    }

    /**
     * Step 2: Verify the 6-digit code.
     * On success, marks the challenge verified and issues a short-lived registration token.
     */
    @Transactional
    public VerifyCodeResponse verifyCode(VerifyCodeRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String enteredRegisterNumber = request.getRegisterNumber().trim();
        String enteredCode = request.getCode().trim();

        StudentAccessCode sac = findAuthorizedStudent(email, enteredRegisterNumber);

        LocalDateTime now = LocalDateTime.now();
        List<RegistrationVerificationCode> activeCodes = verificationCodeRepository.findActiveByAuthorizedStudentId(sac.getId(), now);
        if (activeCodes.isEmpty()) {
            throw new BadRequestException("No active verification code found or code has expired. Please request a new code.");
        }

        RegistrationVerificationCode rvc = activeCodes.get(0);

        // Check attempt limit
        if (rvc.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            rvc.setUsedAt(now);
            verificationCodeRepository.save(rvc);
            throw new BadRequestException("Too many failed attempts. This code is now invalid. Please request a new code.");
        }

        // Check code match
        if (!passwordEncoder.matches(enteredCode, rvc.getCodeHash())) {
            rvc.setAttemptCount(rvc.getAttemptCount() + 1);
            if (rvc.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
                rvc.setUsedAt(now);
                verificationCodeRepository.save(rvc);
                throw new BadRequestException("Too many failed attempts. This code is now invalid. Please request a new code.");
            }
            verificationCodeRepository.save(rvc);
            int remaining = MAX_VERIFY_ATTEMPTS - rvc.getAttemptCount();
            throw new BadRequestException("Invalid verification code. " + remaining + " attempts remaining.");
        }

        // Valid code
        rvc.setVerifiedAt(now);
        verificationCodeRepository.save(rvc);

        String registrationToken = tokenService.generateRegistrationToken(
                sac.getId(), email, sac.getRegisterNumber(), rvc.getId());

        log.info("Verification code successfully verified for student: {} ({})", maskEmail(email), sac.getRegisterNumber());

        return VerifyCodeResponse.builder()
                .message("Email verified successfully.")
                .registrationToken(registrationToken)
                .build();
    }

    /**
     * Step 3: Complete registration by choosing a password.
     * Enforces roster identity (name and department come strictly from the authorized record).
     */
    @Transactional
    public CompleteRegistrationResponse completeRegistration(CompleteRegistrationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }
        if (request.getPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long.");
        }

        // 1. Validate registration token
        RegistrationTokenClaims claims = tokenService.validateRegistrationToken(request.getRegistrationToken());

        // 2. Validate verification challenge state
        RegistrationVerificationCode rvc = verificationCodeRepository.findById(claims.getVerificationCodeId())
                .orElseThrow(() -> new BadRequestException("Verification challenge not found."));

        if (rvc.getVerifiedAt() == null || rvc.getUsedAt() != null || rvc.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification challenge has expired or was already used.");
        }

        // 3. Validate authorized student state
        StudentAccessCode sac = accessCodeRepository.findById(claims.getAuthorizedStudentId())
                .orElseThrow(() -> new BadRequestException("Authorized student record not found."));

        if (sac.getUsedAt() != null || Boolean.FALSE.equals(sac.getActive())) {
            throw new ConflictException("This account is already registered. Sign in instead.");
        }

        if (userRepository.existsByEmail(claims.getEmail())) {
            throw new ConflictException("This account is already registered. Sign in instead.");
        }
        if (studentProfileRepository.existsByRegisterNumber(sac.getRegisterNumber())) {
            throw new ConflictException("An account already exists for this register number.");
        }

        // 4. Resolve authoritative department
        Department department = departmentResolver.resolve(sac.getDepartmentCode())
                .orElseThrow(() -> new BadRequestException("Department unavailable."));
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new BadRequestException("Department unavailable.");
        }

        // 5. Authoritatively create STUDENT user and StudentProfile
        User user = User.builder()
                .name(sac.getName())
                .email(claims.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.STUDENT)
                .department(department)
                .active(true)
                .build();
        user = userRepository.save(user);

        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .registerNumber(sac.getRegisterNumber())
                .build();
        profile = studentProfileRepository.save(profile);

        // 6. Mark authorized record as consumed
        sac.setStudentProfile(profile);
        sac.setEmail(claims.getEmail());
        sac.setUsedAt(LocalDateTime.now());
        sac.setActive(false);
        accessCodeRepository.save(sac);

        // 7. Mark verification code challenge as consumed
        rvc.setUsedAt(LocalDateTime.now());
        verificationCodeRepository.save(rvc);

        auditService.log("STUDENT_SELF_REGISTRATION", "User", user.getId(), user.getEmail());
        log.info("Student successfully self-registered: {} ({})", user.getEmail(), sac.getRegisterNumber());

        return CompleteRegistrationResponse.builder()
                .message("Account created successfully. You can now sign in.")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    private StudentAccessCode findAuthorizedStudent(String email, String enteredRegisterNumber) {
        String enteredTail = lastFiveNumericDigits(enteredRegisterNumber);
        if (enteredTail.isEmpty()) {
            throw new BadRequestException("Register number does not match any student record.");
        }

        List<StudentAccessCode> candidates = accessCodeRepository.findByRegisterNumberEndingWith(enteredTail).stream()
                .filter(c -> lastFiveNumericDigits(c.getRegisterNumber()).equals(enteredTail))
                .filter(c -> c.getEmail() != null && c.getEmail().trim().equalsIgnoreCase(email))
                .toList();

        if (candidates.isEmpty()) {
            Optional<StudentAccessCode> exact = accessCodeRepository.findByRegisterNumber(enteredRegisterNumber);
            if (exact.isPresent() && exact.get().getEmail() != null && exact.get().getEmail().trim().equalsIgnoreCase(email)) {
                candidates = List.of(exact.get());
            }
        }

        if (candidates.isEmpty()) {
            throw new BadRequestException("No authorized student record found for this email and register number. Please contact your Placement Officer.");
        }

        return candidates.get(0);
    }

    private String maskEmail(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx <= 1) return email;
        String name = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (name.length() <= 2) {
            return name.charAt(0) + "*" + domain;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1) + domain;
    }

    private static String lastFiveNumericDigits(String registerNumber) {
        if (registerNumber == null) return "";
        String digits = registerNumber.replaceAll("\\D", "");
        if (digits.length() <= REGISTER_NUMBER_TAIL) return digits;
        return digits.substring(digits.length() - REGISTER_NUMBER_TAIL);
    }
}
