package com.college.placement.staff;

import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.profile.ProfileService;
import com.college.placement.profile.dto.ProfileResponse;
import com.college.placement.staff.dto.StaffProfileResponse;
import com.college.placement.staff.dto.UpdateStaffProfileRequest;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Role routing, lazy creation, partial update and clear behaviour for the
 * staff profile, exercised against the real services and a real database.
 * Nothing is mocked, and the whole test runs in one rolled-back transaction.
 */
@SpringBootTest
@Transactional
class StaffProfileRoutingTest {

    @Autowired
    private StaffProfileService staffProfileService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private StaffProfileRepository staffProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private Department cse;
    private User pc;
    private User po;
    private User student;
    private User pr;

    @BeforeEach
    void setUp() {
        cse = StaffProfileFixtures.department(departmentRepository, "SP7CSE");
        pc = StaffProfileFixtures.user(userRepository, "spPc", Role.PC, cse);
        po = StaffProfileFixtures.user(userRepository, "spPo", Role.PO, cse);
        student = StaffProfileFixtures.user(userRepository, "spStudent", Role.STUDENT, cse);
        pr = StaffProfileFixtures.user(userRepository, "spPr", Role.PR, cse);

        StaffProfileFixtures.studentProfile(studentProfileRepository, student);
        StaffProfileFixtures.studentProfile(studentProfileRepository, pr);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
    }

    private static UpdateStaffProfileRequest request() {
        return new UpdateStaffProfileRequest();
    }

    // ------------------------------------------------------------- lazy create

    @Test
    @DisplayName("5: a staff member with no saved profile reads an all-null section without creating a row")
    void readWithoutRowDoesNotCreate() {
        authenticate(pc);

        StaffProfileResponse response = staffProfileService.getMyStaffProfileOrNull();

        assertThat(response).isNotNull();
        assertThat(response.getPhone()).isNull();
        assertThat(response.getDesignation()).isNull();
        assertThat(response.getOfficeLocation()).isNull();
        assertThat(response.getBio()).isNull();
        assertThat(response.getLinkedinUrl()).isNull();
        assertThat(response.getExpertise()).isNull();
        assertThat(staffProfileRepository.existsByUserId(pc.getId())).isFalse();
    }

    @Test
    @DisplayName("5: the first save creates exactly one row and later saves reuse it")
    void firstSaveCreatesAndLaterSavesReuseTheSameRow() {
        authenticate(pc);

        assertThat(staffProfileRepository.existsByUserId(pc.getId())).isFalse();

        UpdateStaffProfileRequest first = request();
        first.setDesignation("Placement Coordinator");
        staffProfileService.updateMyStaffProfile(first);

        UpdateStaffProfileRequest second = request();
        second.setOfficeLocation("Block C, Room 214");
        staffProfileService.updateMyStaffProfile(second);

        assertThat(staffProfileRepository.findAll())
                .filteredOn(p -> p.getUser().getId().equals(pc.getId()))
                .hasSize(1);

        StaffProfileResponse response = staffProfileService.getMyStaffProfileOrNull();
        assertThat(response.getDesignation()).isEqualTo("Placement Coordinator");
        assertThat(response.getOfficeLocation()).isEqualTo("Block C, Room 214");
    }

    // ----------------------------------------------------------------- routing

    @Test
    @DisplayName("6: PC and PO own a StaffProfile")
    void pcAndPoOwnStaffProfile() {
        authenticate(pc);
        assertThat(staffProfileService.getMyStaffProfileOrNull()).isNotNull();
        assertThat(profileService.getMyProfile().getStaffProfile()).isNotNull();

        authenticate(po);
        assertThat(staffProfileService.getMyStaffProfileOrNull()).isNotNull();
        assertThat(profileService.getMyProfile().getStaffProfile()).isNotNull();
    }

    @Test
    @DisplayName("6: STUDENT and PR keep a StudentProfile and never see a StaffProfile section")
    void studentAndPrKeepStudentProfile() {
        authenticate(student);
        ProfileResponse studentView = profileService.getMyProfile();
        assertThat(studentView.getStudentProfile()).isNotNull();
        assertThat(studentView.getStaffProfile()).isNull();

        authenticate(pr);
        ProfileResponse prView = profileService.getMyProfile();
        assertThat(prView.getStudentProfile()).isNotNull();
        assertThat(prView.getStaffProfile()).isNull();
    }

    @Test
    @DisplayName("8: STUDENT and PR cannot save a staff profile")
    void studentAndPrCannotSaveStaffProfile() {
        authenticate(student);
        assertThatThrownBy(() -> staffProfileService.updateMyStaffProfile(request()))
                .isInstanceOf(ForbiddenException.class);

        authenticate(pr);
        assertThatThrownBy(() -> staffProfileService.updateMyStaffProfile(request()))
                .isInstanceOf(ForbiddenException.class);

        assertThat(staffProfileRepository.findByUserId(student.getId())).isEmpty();
        assertThat(staffProfileRepository.findByUserId(pr.getId())).isEmpty();
    }

    // ------------------------------------------------------ partial / clearing

    @Test
    @DisplayName("21: saving a single field leaves the others untouched")
    void savingOneFieldLeavesOthersUntouched() {
        authenticate(pc);
        StaffProfileFixtures.profile(staffProfileRepository, pc, "Placement Coordinator");

        UpdateStaffProfileRequest request = request();
        request.setPhone("+91 98765 43210");
        staffProfileService.updateMyStaffProfile(request);

        StaffProfileResponse response = staffProfileService.getMyStaffProfileOrNull();
        assertThat(response.getPhone()).isEqualTo("+91 98765 43210");
        assertThat(response.getDesignation()).isEqualTo("Placement Coordinator");
        assertThat(response.getExpertise()).containsExactly("Java");
    }

    @Test
    @DisplayName("53: an empty string clears the stored value")
    void emptyStringClearsTheField() {
        authenticate(pc);
        StaffProfile profile = StaffProfileFixtures.profile(staffProfileRepository, pc, "Placement Coordinator");
        profile.setPhone("+91 98765 43210");
        profile.setLinkedinUrl("https://www.linkedin.com/in/old");
        staffProfileRepository.saveAndFlush(profile);

        UpdateStaffProfileRequest request = request();
        request.setLinkedinUrl("");
        staffProfileService.updateMyStaffProfile(request);

        StaffProfileResponse response = staffProfileService.getMyStaffProfileOrNull();
        assertThat(response.getLinkedinUrl()).isNull();
        assertThat(response.getPhone()).isEqualTo("+91 98765 43210");
    }

    @Test
    @DisplayName("21: values are trimmed before they are stored")
    void valuesAreTrimmed() {
        authenticate(pc);

        UpdateStaffProfileRequest request = request();
        request.setDesignation("  Assistant Placement Officer  ");
        request.setOfficeLocation("  Block A  ");
        staffProfileService.updateMyStaffProfile(request);

        StaffProfileResponse response = staffProfileService.getMyStaffProfileOrNull();
        assertThat(response.getDesignation()).isEqualTo("Assistant Placement Officer");
        assertThat(response.getOfficeLocation()).isEqualTo("Block A");
    }

    // ---------------------------------------------------------------- expertise

    @Test
    @DisplayName("28: expertise is trimmed, de-duplicated case-insensitively and keeps its order")
    void expertiseIsNormalized() {
        authenticate(pc);

        UpdateStaffProfileRequest request = request();
        request.setExpertise(Arrays.asList("  Java ", "Spring Boot", "java", "SQL", ""));
        staffProfileService.updateMyStaffProfile(request);

        assertThat(staffProfileService.getMyStaffProfileOrNull().getExpertise())
                .containsExactly("Java", "Spring Boot", "SQL");
    }

    @Test
    @DisplayName("28: an all-blank expertise list clears the tags")
    void blankExpertiseClearsTags() {
        authenticate(pc);
        StaffProfile profile = StaffProfileFixtures.profile(staffProfileRepository, pc, "Placement Coordinator");

        UpdateStaffProfileRequest request = request();
        request.setExpertise(List.of("  ", ""));
        staffProfileService.updateMyStaffProfile(request);

        assertThat(staffProfileRepository.findByUserId(pc.getId()).orElseThrow().getExpertise()).isNull();
        assertThat(profile.getUser().getId()).isEqualTo(pc.getId());
    }

    // ------------------------------------------------------------- url safety

    @Test
    @DisplayName("30: a script-bearing or malformed LinkedIn URL is refused with a 400-style error")
    void unsafeUrlIsRefused() {
        authenticate(pc);

        UpdateStaffProfileRequest request = request();
        request.setLinkedinUrl("javascript:alert(1)");

        assertThatThrownBy(() -> staffProfileService.updateMyStaffProfile(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("29: a well-formed https LinkedIn URL is stored")
    void httpsUrlIsStored() {
        authenticate(pc);

        UpdateStaffProfileRequest request = request();
        request.setLinkedinUrl("https://www.linkedin.com/in/jane-doe");
        staffProfileService.updateMyStaffProfile(request);

        assertThat(staffProfileService.getMyStaffProfileOrNull().getLinkedinUrl())
                .isEqualTo("https://www.linkedin.com/in/jane-doe");
    }

    // ------------------------------------------------- single source of truth

    @Test
    @DisplayName("31: name, email, role and department are not duplicated into the staff profile")
    void identityFieldsAreNotDuplicated() {
        authenticate(pc);
        pc.setName("Renamed Coordinator");
        userRepository.saveAndFlush(pc);

        UpdateStaffProfileRequest request = request();
        request.setDesignation("Placement Coordinator");
        staffProfileService.updateMyStaffProfile(request);

        ProfileResponse response = profileService.getMyProfile();
        assertThat(response.getName()).isEqualTo("Renamed Coordinator");
        assertThat(response.getEmail()).isEqualTo(pc.getEmail());
        assertThat(response.getRole()).isEqualTo("PC");
        assertThat(response.getDepartmentName()).isEqualTo(cse.getName());
        assertThat(response.getStaffProfile().getDesignation()).isEqualTo("Placement Coordinator");
    }

    @Test
    @DisplayName("31: a department change is reflected immediately from the authoritative assignment")
    void departmentChangeIsReflectedFromTheUserAssignment() {
        Department ece = StaffProfileFixtures.department(departmentRepository, "SP7ECE");
        authenticate(pc);

        UpdateStaffProfileRequest request = request();
        request.setDesignation("Placement Coordinator");
        staffProfileService.updateMyStaffProfile(request);

        pc.setDepartment(ece);
        userRepository.saveAndFlush(pc);

        assertThat(profileService.getMyProfile().getDepartmentName()).isEqualTo(ece.getName());
    }

    // ------------------------------------------------------------ role change

    @Test
    @DisplayName("49: a role change away from PC/PO stops the staff section being served")
    void roleChangeAwayFromStaffStopsServingTheSection() {
        authenticate(pc);
        UpdateStaffProfileRequest request = request();
        request.setDesignation("Placement Coordinator");
        staffProfileService.updateMyStaffProfile(request);

        pc.setRole(Role.PR);
        userRepository.saveAndFlush(pc);

        assertThat(staffProfileService.getMyStaffProfileOrNull()).isNull();
        assertThat(profileService.getMyProfile().getStaffProfile()).isNull();
    }

    // ------------------------------------------------------------- uniqueness

    @Test
    @DisplayName("56: one staff profile row per user is enforced by the unique constraint")
    void oneProfilePerUserIsEnforced() {
        authenticate(pc);
        UpdateStaffProfileRequest request = request();
        request.setDesignation("Placement Coordinator");
        staffProfileService.updateMyStaffProfile(request);

        assertThatThrownBy(() -> staffProfileRepository.saveAndFlush(StaffProfile.builder()
                .user(pc)
                .designation("Duplicate")
                .build())).isInstanceOf(Exception.class);
    }
}
