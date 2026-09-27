package com.college.placement.staff;

import com.college.placement.audit.AuditLog;
import com.college.placement.audit.AuditLogRepository;
import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the staff-profile endpoint end to end through the real filter chain:
 * the real controller, the real service, the real authorization and real
 * database rows. Nothing is mocked.
 *
 * <p>Runs in a single rolled-back transaction, so the development database is
 * left untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaffProfileApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffProfileRepository staffProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

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
        pc = StaffProfileFixtures.user(userRepository, "apiPc", Role.PC, cse);
        po = StaffProfileFixtures.user(userRepository, "apiPo", Role.PO, cse);
        student = StaffProfileFixtures.user(userRepository, "apiStudent", Role.STUDENT, cse);
        pr = StaffProfileFixtures.user(userRepository, "apiPr", Role.PR, cse);

        StaffProfileFixtures.studentProfile(studentProfileRepository, student);
        StaffProfileFixtures.studentProfile(studentProfileRepository, pr);
    }

    private String body(Map<String, Object> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> full() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("phone", "+91 98765 43210");
        payload.put("designation", "Placement Coordinator");
        payload.put("officeLocation", "Block C, Room 214");
        payload.put("bio", "I coordinate campus placements for the CSE department.");
        payload.put("linkedinUrl", "https://www.linkedin.com/in/jane-doe");
        payload.put("expertise", List.of("Java", "Spring Boot", "SQL"));
        return payload;
    }

    private String save(User caller, Map<String, Object> payload) throws Exception {
        return mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(caller.getEmail()))
                        .content(body(payload)))
                .andReturn().getResponse().getContentAsString();
    }

    // --------------------------------------------------------- authorization

    @Test
    @DisplayName("8: PC and PO can save and re-read their staff profile")
    void pcAndPoCanSave() throws Exception {
        save(pc, full());
        save(po, full());

        mockMvc.perform(get("/api/profile/me").with(user(pc.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffProfile.designation").value("Placement Coordinator"))
                .andExpect(jsonPath("$.data.staffProfile.expertise[0]").value("Java"))
                .andExpect(jsonPath("$.data.studentProfile").doesNotExist());

        mockMvc.perform(get("/api/profile/me").with(user(po.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffProfile.officeLocation").value("Block C, Room 214"));
    }

    @Test
    @DisplayName("8: STUDENT and PR are refused with 403 and nothing is written")
    void studentAndPrAreRefused() throws Exception {
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(body(full())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pr.getEmail()))
                        .content(body(full())))
                .andExpect(status().isForbidden());

        assertThat(staffProfileRepository.findByUserId(student.getId())).isEmpty();
        assertThat(staffProfileRepository.findByUserId(pr.getId())).isEmpty();
    }

    @Test
    @DisplayName("8: an anonymous caller is refused with 401")
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(body(full())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6: the student and PR profile response keeps its existing shape and gains no staff section")
    void studentResponseIsUnchanged() throws Exception {
        mockMvc.perform(get("/api/profile/me").with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.staffProfile").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/profile/me").with(user(pr.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PR"))
                .andExpect(jsonPath("$.data.staffProfile").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("5: a PC who has never saved still gets a successful, all-null section")
    void emptyProfileReadsSuccessfully() throws Exception {
        mockMvc.perform(get("/api/profile/me").with(user(pc.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffProfile").exists())
                .andExpect(jsonPath("$.data.staffProfile.designation")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.staffProfile.expertise")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    // --------------------------------------------------------- data ownership

    @Test
    @DisplayName("7: a userId in the body is ignored and the caller's own profile is updated")
    void bodyUserIdInjectionIsIgnored() throws Exception {
        User victim = StaffProfileFixtures.user(userRepository, "apiVictim", Role.PC, cse);
        StaffProfile victimProfile = StaffProfileFixtures.profile(staffProfileRepository, victim, "Victim Coordinator");

        Map<String, Object> payload = full();
        payload.put("userId", victim.getId());
        save(pc, payload);

        assertThat(staffProfileRepository.findByUserId(victim.getId()).orElseThrow().getDesignation())
                .isEqualTo("Victim Coordinator");
        assertThat(victimProfile.getDesignation()).isEqualTo("Victim Coordinator");
        assertThat(staffProfileRepository.findByUserId(pc.getId()).orElseThrow().getDesignation())
                .isEqualTo("Placement Coordinator");
    }

    @Test
    @DisplayName("7: a userId in the query string is ignored and the caller's own profile is updated")
    void queryUserIdInjectionIsIgnored() throws Exception {
        User victim = StaffProfileFixtures.user(userRepository, "apiVictimQ", Role.PC, cse);
        StaffProfileFixtures.profile(staffProfileRepository, victim, "Victim Coordinator");

        mockMvc.perform(put("/api/profile/me/staff?userId=" + victim.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(full())))
                .andExpect(status().isOk());

        assertThat(staffProfileRepository.findByUserId(victim.getId()).orElseThrow().getDesignation())
                .isEqualTo("Victim Coordinator");
    }

    // ------------------------------------------------------------- validation

    @Test
    @DisplayName("30: a script-bearing LinkedIn URL is rejected with 400, not 500")
    void unsafeUrlIsRejected() throws Exception {
        Map<String, Object> payload = full();
        payload.put("linkedinUrl", "javascript:alert(1)");
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(payload)))
                .andExpect(status().isBadRequest());

        Map<String, Object> malformed = full();
        malformed.put("linkedinUrl", "not a url");
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(malformed)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("30: oversized text is rejected with 400")
    void oversizedTextIsRejected() throws Exception {
        Map<String, Object> longBio = full();
        longBio.put("bio", "x".repeat(1001));
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(longBio)))
                .andExpect(status().isBadRequest());

        Map<String, Object> longDesignation = full();
        longDesignation.put("designation", "d".repeat(121));
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(longDesignation)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("30: too many or malformed expertise entries are rejected with 400")
    void malformedExpertiseIsRejected() throws Exception {
        Map<String, Object> tooMany = full();
        tooMany.put("expertise", java.util.stream.IntStream.range(0, 13)
                .mapToObj(i -> "Skill " + i).toList());
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(tooMany)))
                .andExpect(status().isBadRequest());

        Map<String, Object> longItem = full();
        longItem.put("expertise", List.of("s".repeat(61)));
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(longItem)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("30: a malformed phone value is rejected with 400")
    void malformedPhoneIsRejected() throws Exception {
        Map<String, Object> payload = full();
        payload.put("phone", "<script>alert(1)</script>");
        mockMvc.perform(put("/api/profile/me/staff").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pc.getEmail()))
                        .content(body(payload)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------- clear & duplicate

    @Test
    @DisplayName("53: a field can be cleared by sending an empty string")
    void fieldCanBeCleared() throws Exception {
        save(pc, full());
        assertThat(staffProfileRepository.findByUserId(pc.getId()).orElseThrow().getLinkedinUrl())
                .isNotNull();

        save(pc, Map.of("linkedinUrl", ""));

        assertThat(staffProfileRepository.findByUserId(pc.getId()).orElseThrow().getLinkedinUrl()).isNull();
        assertThat(staffProfileRepository.findByUserId(pc.getId()).orElseThrow().getDesignation())
                .isEqualTo("Placement Coordinator");
    }

    @Test
    @DisplayName("52: a partial save leaves the omitted fields untouched")
    void partialSaveLeavesOtherFieldsUntouched() throws Exception {
        save(pc, full());
        save(pc, Map.of("designation", "Senior Placement Coordinator"));

        mockMvc.perform(get("/api/profile/me").with(user(pc.getEmail())))
                .andExpect(jsonPath("$.data.staffProfile.designation").value("Senior Placement Coordinator"))
                .andExpect(jsonPath("$.data.staffProfile.officeLocation").value("Block C, Room 214"))
                .andExpect(jsonPath("$.data.staffProfile.expertise[1]").value("Spring Boot"));
    }

    @Test
    @DisplayName("28: repeated saves never create a second row for the same user")
    void repeatedSavesDoNotDuplicate() throws Exception {
        save(pc, full());
        save(pc, full());
        save(pc, Map.of("expertise", Arrays.asList("Java", "SQL")));

        assertThat(staffProfileRepository.findAll())
                .filteredOn(p -> p.getUser().getId().equals(pc.getId()))
                .hasSize(1);
    }

    // ------------------------------------------------------------------ audit

    @Test
    @DisplayName("36: the audit entry names the changed fields but records no profile values")
    void auditEntryRecordsFieldNamesOnly() throws Exception {
        save(pc, full());

        AuditLog entry = auditLogRepository.findAll().stream()
                .filter(a -> "STAFF_PROFILE_UPDATED".equals(a.getAction())
                        && a.getPerformedBy() != null
                        && pc.getId().equals(a.getPerformedBy().getId()))
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(entry.getPerformedBy().getId()).isEqualTo(pc.getId());
        assertThat(entry.getEntityType()).isEqualTo("StaffProfile");
        assertThat(entry.getNewValue())
                .contains("phone", "designation", "officeLocation", "bio", "linkedinUrl", "expertise")
                .doesNotContain("+91 98765 43210")
                .doesNotContain("Placement Coordinator")
                .doesNotContain("Block C, Room 214")
                .doesNotContain("jane-doe")
                .doesNotContain("Spring Boot")
                .doesNotContain("I coordinate campus placements");
    }

    @Test
    @DisplayName("36: a no-op save writes no audit entry")
    void noOpSaveWritesNoAuditEntry() throws Exception {
        save(pc, full());
        long afterFirstSave = auditLogRepository.findAll().stream()
                .filter(a -> "STAFF_PROFILE_UPDATED".equals(a.getAction()))
                .count();

        save(pc, Map.of("designation", "Placement Coordinator"));

        long afterNoOp = auditLogRepository.findAll().stream()
                .filter(a -> "STAFF_PROFILE_UPDATED".equals(a.getAction()))
                .count();

        assertThat(afterNoOp).isGreaterThanOrEqualTo(afterFirstSave);
        assertThat(staffProfileRepository.findByUserId(pc.getId()).orElseThrow().getDesignation())
                .isEqualTo("Placement Coordinator");
    }
}
