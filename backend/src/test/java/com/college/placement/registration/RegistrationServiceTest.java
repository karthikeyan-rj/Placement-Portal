package com.college.placement.registration;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.RateLimitException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private StudentAccessCodeRepository accessCodeRepository;
    @Mock
    private RegistrationVerificationCodeRepository verificationCodeRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentCodeResolver departmentResolver;
    @Mock
    private RegistrationCodeGenerator codeGenerator;
    @Mock
    private RegistrationTokenService tokenService;
    @Mock
    private EmailDeliveryService emailDeliveryService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private RegistrationService registrationService;

    private StudentAccessCode mockRosterRecord;
    private Department mockDepartment;

    @BeforeEach
    void setUp() {
        mockDepartment = Department.builder()
                .id(1L)
                .name("Computer Science and Engineering")
                .active(true)
                .build();

        mockRosterRecord = StudentAccessCode.builder()
                .id(100L)
                .name("Karthikeyan RJ")
                .email("karthikeyanrj@student.tce.edu")
                .registerNumber("24C21031")
                .departmentCode("CSE")
                .active(true)
                .codeHash("hash123")
                .build();
    }

    @Test
    void testRequestCode_UnauthorizedEmail_DomainRejected() {
        RequestCodeRequest request = new RequestCodeRequest("attacker@gmail.com", "24C21031");

        assertThatThrownBy(() -> registrationService.requestCode(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not authorized for registration");
    }

    @Test
    void testRequestCode_AlreadyRegisteredEmail_Conflict() {
        RequestCodeRequest request = new RequestCodeRequest("karthikeyanrj@student.tce.edu", "24C21031");
        when(userRepository.existsByEmail("karthikeyanrj@student.tce.edu")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.requestCode(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void testRequestCode_AlreadyRegisteredRegNo_Conflict() {
        RequestCodeRequest request = new RequestCodeRequest("karthikeyanrj@student.tce.edu", "24C21031");
        when(userRepository.existsByEmail("karthikeyanrj@student.tce.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRegisterNumber("24C21031")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.requestCode(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void testRequestCode_NotInRoster_BadRequest() {
        RequestCodeRequest request = new RequestCodeRequest("unknown@student.tce.edu", "24C99999");
        when(userRepository.existsByEmail("unknown@student.tce.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRegisterNumber("24C99999")).thenReturn(false);
        when(accessCodeRepository.findByRegisterNumberEndingWith("99999")).thenReturn(Collections.emptyList());
        when(accessCodeRepository.findByRegisterNumber("24C99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.requestCode(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No authorized student record found");
    }

    @Test
    void testRequestCode_AlreadyConsumed_Conflict() {
        mockRosterRecord.setUsedAt(LocalDateTime.now().minusDays(1));
        RequestCodeRequest request = new RequestCodeRequest("karthikeyanrj@student.tce.edu", "24C21031");
        when(userRepository.existsByEmail("karthikeyanrj@student.tce.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRegisterNumber("24C21031")).thenReturn(false);
        when(accessCodeRepository.findByRegisterNumberEndingWith("21031")).thenReturn(List.of(mockRosterRecord));

        assertThatThrownBy(() -> registrationService.requestCode(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void testRequestCode_CooldownEnforced_RateLimit() {
        RequestCodeRequest request = new RequestCodeRequest("karthikeyanrj@student.tce.edu", "24C21031");
        when(userRepository.existsByEmail("karthikeyanrj@student.tce.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRegisterNumber("24C21031")).thenReturn(false);
        when(accessCodeRepository.findByRegisterNumberEndingWith("21031")).thenReturn(List.of(mockRosterRecord));

        RegistrationVerificationCode recentCode = RegistrationVerificationCode.builder()
                .id(1L)
                .createdAt(LocalDateTime.now().minusSeconds(30))
                .build();
        when(verificationCodeRepository.findActiveByAuthorizedStudentId(eq(100L), any(LocalDateTime.class)))
                .thenReturn(List.of(recentCode));

        assertThatThrownBy(() -> registrationService.requestCode(request))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Please wait 60 seconds");
    }

    @Test
    void testRequestCode_MaxPerHour_RateLimit() {
        RequestCodeRequest request = new RequestCodeRequest("karthikeyanrj@student.tce.edu", "24C21031");
        when(userRepository.existsByEmail("karthikeyanrj@student.tce.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRegisterNumber("24C21031")).thenReturn(false);
        when(accessCodeRepository.findByRegisterNumberEndingWith("21031")).thenReturn(List.of(mockRosterRecord));
        when(verificationCodeRepository.findActiveByAuthorizedStudentId(eq(100L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(verificationCodeRepository.countByEmailSince(eq("karthikeyanrj@student.tce.edu"), any(LocalDateTime.class)))
                .thenReturn(5L);

        assertThatThrownBy(() -> registrationService.requestCode(request))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Too many verification code requests");
    }

    @Test
    void testRequestCode_Success() {
        RequestCodeRequest request = new RequestCodeRequest("karthikeyanrj@student.tce.edu", "24C21031");
        when(userRepository.existsByEmail("karthikeyanrj@student.tce.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRegisterNumber("24C21031")).thenReturn(false);
        when(accessCodeRepository.findByRegisterNumberEndingWith("21031")).thenReturn(List.of(mockRosterRecord));
        when(verificationCodeRepository.findActiveByAuthorizedStudentId(eq(100L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(verificationCodeRepository.countByEmailSince(eq("karthikeyanrj@student.tce.edu"), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(codeGenerator.generate6DigitCode()).thenReturn("482731");
        when(passwordEncoder.encode("482731")).thenReturn("hashed482731");

        RequestCodeResponse response = registrationService.requestCode(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("We sent a verification code to");
        assertThat(response.getMaskedEmail()).contains("@student.tce.edu");

        verify(emailDeliveryService).sendRegistrationCode("karthikeyanrj@student.tce.edu", "482731");
        verify(verificationCodeRepository).save(any(RegistrationVerificationCode.class));
    }

    @Test
    void testVerifyCode_WrongCode_IncrementsAttempts_BlockedAfterLimit() {
        VerifyCodeRequest request = new VerifyCodeRequest("karthikeyanrj@student.tce.edu", "24C21031", "000000");
        when(accessCodeRepository.findByRegisterNumberEndingWith("21031")).thenReturn(List.of(mockRosterRecord));

        RegistrationVerificationCode rvc = RegistrationVerificationCode.builder()
                .id(1L)
                .codeHash("hashedCode")
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(verificationCodeRepository.findActiveByAuthorizedStudentId(eq(100L), any(LocalDateTime.class)))
                .thenReturn(List.of(rvc));
        when(passwordEncoder.matches("000000", "hashedCode")).thenReturn(false);

        // 1st wrong attempt
        assertThatThrownBy(() -> registrationService.verifyCode(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid verification code. 4 attempts remaining.");
        assertThat(rvc.getAttemptCount()).isEqualTo(1);

        // Max attempts reached
        rvc.setAttemptCount(5);
        assertThatThrownBy(() -> registrationService.verifyCode(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many failed attempts");
    }

    @Test
    void testVerifyCode_Success_ReturnsRegistrationToken() {
        VerifyCodeRequest request = new VerifyCodeRequest("karthikeyanrj@student.tce.edu", "24C21031", "482731");
        when(accessCodeRepository.findByRegisterNumberEndingWith("21031")).thenReturn(List.of(mockRosterRecord));

        RegistrationVerificationCode rvc = RegistrationVerificationCode.builder()
                .id(1L)
                .codeHash("hashedCode")
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(verificationCodeRepository.findActiveByAuthorizedStudentId(eq(100L), any(LocalDateTime.class)))
                .thenReturn(List.of(rvc));
        when(passwordEncoder.matches("482731", "hashedCode")).thenReturn(true);
        when(tokenService.generateRegistrationToken(eq(100L), eq("karthikeyanrj@student.tce.edu"), eq("24C21031"), eq(1L)))
                .thenReturn("mock-registration-jwt");

        VerifyCodeResponse response = registrationService.verifyCode(request);

        assertThat(response.getRegistrationToken()).isEqualTo("mock-registration-jwt");
        assertThat(rvc.getVerifiedAt()).isNotNull();
    }

    @Test
    void testCompleteRegistration_PasswordMismatch_BadRequest() {
        CompleteRegistrationRequest request = new CompleteRegistrationRequest("token", "Password123!", "Mismatch123!");

        assertThatThrownBy(() -> registrationService.completeRegistration(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Passwords do not match");
    }

    @Test
    void testCompleteRegistration_ShortPassword_BadRequest() {
        CompleteRegistrationRequest request = new CompleteRegistrationRequest("token", "short", "short");

        assertThatThrownBy(() -> registrationService.completeRegistration(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 8 characters");
    }

    @Test
    void testCompleteRegistration_Success_CreatesStudentOnly() {
        CompleteRegistrationRequest request = new CompleteRegistrationRequest("valid-token", "ValidPassword123!", "ValidPassword123!");

        RegistrationTokenClaims claims = RegistrationTokenClaims.builder()
                .authorizedStudentId(100L)
                .email("karthikeyanrj@student.tce.edu")
                .registerNumber("24C21031")
                .verificationCodeId(1L)
                .build();
        when(tokenService.validateRegistrationToken("valid-token")).thenReturn(claims);

        RegistrationVerificationCode rvc = RegistrationVerificationCode.builder()
                .id(1L)
                .verifiedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(verificationCodeRepository.findById(1L)).thenReturn(Optional.of(rvc));
        when(accessCodeRepository.findById(100L)).thenReturn(Optional.of(mockRosterRecord));
        when(userRepository.existsByEmail("karthikeyanrj@student.tce.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRegisterNumber("24C21031")).thenReturn(false);
        when(departmentResolver.resolve("CSE")).thenReturn(Optional.of(mockDepartment));
        when(passwordEncoder.encode("ValidPassword123!")).thenReturn("bcryptHash");

        User savedUser = User.builder()
                .id(50L)
                .name("Karthikeyan RJ")
                .email("karthikeyanrj@student.tce.edu")
                .role(Role.STUDENT)
                .department(mockDepartment)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        StudentProfile savedProfile = StudentProfile.builder()
                .id(500L)
                .user(savedUser)
                .registerNumber("24C21031")
                .build();
        when(studentProfileRepository.save(any(StudentProfile.class))).thenReturn(savedProfile);

        CompleteRegistrationResponse response = registrationService.completeRegistration(request);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(50L);
        assertThat(response.getRole()).isEqualTo("STUDENT");
        assertThat(response.getEmail()).isEqualTo("karthikeyanrj@student.tce.edu");

        // Verify authorized student code and verification code marked as consumed
        assertThat(mockRosterRecord.getUsedAt()).isNotNull();
        assertThat(mockRosterRecord.getActive()).isFalse();
        assertThat(rvc.getUsedAt()).isNotNull();

        verify(auditService).log(eq("STUDENT_SELF_REGISTRATION"), eq("User"), eq(50L), eq("karthikeyanrj@student.tce.edu"));
    }
}
