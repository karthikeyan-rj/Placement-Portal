package com.college.placement.mockinterview;

import com.college.placement.common.enums.PrepDifficulty;
import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.mockinterview.domain.MockInterviewSessionQuestion;
import com.college.placement.mockinterview.repository.MockInterviewSessionQuestionRepository;
import com.college.placement.mockinterview.repository.MockInterviewSessionRepository;
import com.college.placement.preparation.repository.PrepModuleRepository;
import com.college.placement.preparation.repository.PrepQuestionRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of the Mock Interview HTTP surface through the real filter
 * chain: real controller, real service, real authorization, real database and
 * the real certified preparation bank. Nothing is mocked, and the whole class
 * runs in one rolled-back transaction.
 *
 * <p>No question content is created for these tests on purpose: the feature
 * exists precisely to reuse the existing bank, so the tests read that bank.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MockInterviewApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PrepModuleRepository prepModuleRepository;

    @Autowired
    private PrepQuestionRepository prepQuestionRepository;

    @Autowired
    private MockInterviewSessionRepository sessionRepository;

    @Autowired
    private MockInterviewSessionQuestionRepository sessionQuestionRepository;

    private Department cse;
    private User student;
    private User pr;
    private User pc;
    private User po;
    private User otherStudentUser;

    @BeforeEach
    void setUp() {
        cse = MockInterviewFixtures.department(departmentRepository, "MI");
        student = MockInterviewFixtures.user(userRepository, "miStudent", Role.STUDENT, cse);
        pr = MockInterviewFixtures.user(userRepository, "miPr", Role.PR, cse);
        pc = MockInterviewFixtures.user(userRepository, "miPc", Role.PC, cse);
        po = MockInterviewFixtures.user(userRepository, "miPo", Role.PO, cse);

        MockInterviewFixtures.studentProfile(studentProfileRepository, student);
        MockInterviewFixtures.studentProfile(studentProfileRepository, pr);

        otherStudentUser = MockInterviewFixtures.user(userRepository, "miOther", Role.STUDENT, cse);
        MockInterviewFixtures.studentProfile(studentProfileRepository, otherStudentUser);
    }

    // ================================================================
    // helpers
    // ================================================================

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> startPayload() {
        Map<String, Object> p = new HashMap<>();
        p.put("interviewType", "TECHNICAL");
        p.put("difficulty", "MIXED");
        p.put("questionCount", 5);
        // Deterministic so a failure is reproducible.
        p.put("randomSeed", 4242L);
        return p;
    }

    private String postStart(User caller) throws Exception {
        return mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(caller.getEmail()))
                        .content(json(startPayload())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long startSession(User caller) throws Exception {
        return data(postStart(caller)).path("id").asLong();
    }

    private JsonNode data(String body) throws Exception {
        return objectMapper.readTree(body).path("data");
    }

    private long studentProfileId(User u) {
        return studentProfileRepository.findByUserId(u.getId()).orElseThrow().getId();
    }

    private List<MockInterviewSessionQuestion> rows(long sessionId) {
        return sessionQuestionRepository.findSessionDetail(sessionId);
    }

    private void answer(long sessionId, long sessionQuestionId, User caller, String text) throws Exception {
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sessionQuestionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(caller.getEmail()))
                        .content(json(Map.of("studentAnswer", text))))
                .andExpect(status().isOk());
    }

    private void rate(long sessionId, long sessionQuestionId, User caller, String rating) throws Exception {
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sessionQuestionId + "/self-rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(caller.getEmail()))
                        .content(json(Map.of("selfRating", rating))))
                .andExpect(status().isOk());
    }

    // ================================================================
    // Access
    // ================================================================

    @Test
    @DisplayName("STUDENT and PR can use Mock Interview; PC and PO are refused with 403 on every endpoint")
    void accessMatrix() throws Exception {
        long studentSession = startSession(student);
        mockMvc.perform(get("/api/mock-interviews/" + studentSession)
                .with(user(student.getEmail()))).andExpect(status().isOk());

        long prSession = startSession(pr);
        mockMvc.perform(get("/api/mock-interviews/" + prSession)
                .with(user(pr.getEmail()))).andExpect(status().isOk());

        for (User staff : List.of(pc, po)) {
            mockMvc.perform(get("/api/mock-interviews/options")
                    .with(user(staff.getEmail()))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/mock-interviews/me")
                    .with(user(staff.getEmail()))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/mock-interviews/me/active")
                    .with(user(staff.getEmail()))).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/mock-interviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(user(staff.getEmail()))
                            .content(json(startPayload())))
                    .andExpect(status().isForbidden());
        }

        assertThat(sessionRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("anonymous is 401")
    void anonymousIsUnauthorised() throws Exception {
        mockMvc.perform(get("/api/mock-interviews/options")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mock-interviews/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mock-interviews/me/active")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(startPayload())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Student A cannot read, answer, rate, complete or abandon Student B's session")
    void crossStudentIsBlocked() throws Exception {
        long sessionId = startSession(student);
        long sqId = rows(sessionId).get(0).getId();

        mockMvc.perform(get("/api/mock-interviews/" + sessionId)
                .with(user(otherStudentUser.getEmail()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/mock-interviews/" + sessionId + "/results")
                .with(user(otherStudentUser.getEmail()))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(otherStudentUser.getEmail()))
                        .content(json(Map.of("studentAnswer", "intruding"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/self-rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(otherStudentUser.getEmail()))
                        .content(json(Map.of("selfRating", "CONFIDENT"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/complete")
                .with(user(otherStudentUser.getEmail()))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/abandon")
                .with(user(otherStudentUser.getEmail()))).andExpect(status().isNotFound());

        // Nothing was written and the session is untouched.
        assertThat(rows(sessionId).get(0).getStudentAnswer()).isNull();
        assertThat(sessionRepository.findById(sessionId).orElseThrow().isInProgress()).isTrue();
        // And B cannot see it in their own history.
        mockMvc.perform(get("/api/mock-interviews/me")
                        .with(user(otherStudentUser.getEmail())))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ================================================================
    // Options come from the real bank
    // ================================================================

    @Test
    @DisplayName("options report real availability, admit no objective scoring, and cover all three modes")
    void optionsReflectRealBank() throws Exception {
        String body = mockMvc.perform(get("/api/mock-interviews/options")
                        .with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectiveScoringSupported").value(false))
                .andExpect(jsonPath("$.data.maxAnswerLength").value(5000))
                .andExpect(jsonPath("$.data.allowedQuestionCounts",
                        org.hamcrest.Matchers.contains(5, 10, 15, 20)))
                .andExpect(jsonPath("$.data.modes.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode options = data(body);
        Set<String> modeCodes = new HashSet<>();
        for (JsonNode mode : options.path("modes")) {
            modeCodes.add(mode.path("code").asText());
            assertThat(mode.path("label").asText()).isNotBlank();
            assertThat(mode.path("modules").size())
                    .as("mode %s offers modules", mode.path("code").asText())
                    .isGreaterThan(0);
            // EASY + MEDIUM + HARD must reconcile with the MIXED total.
            int sum = mode.path("availableByDifficulty").path("EASY").asInt()
                    + mode.path("availableByDifficulty").path("MEDIUM").asInt()
                    + mode.path("availableByDifficulty").path("HARD").asInt();
            assertThat(mode.path("availableByDifficulty").path("MIXED").asInt()).isEqualTo(sum);
        }
        assertThat(modeCodes).containsExactlyInAnyOrder("TECHNICAL", "HR_BEHAVIORAL", "MIXED");

        // MIXED spans every active module, so its total must equal the real
        // count of active questions across active topics and modules.
        List<Long> allModuleIds = prepModuleRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(m -> m.getId()).toList();
        long realTotal = prepQuestionRepository.countActiveForMockInterview(
                allModuleIds, List.of(PrepDifficulty.values()));

        int mixedModeTotal = 0;
        for (JsonNode mode : options.path("modes")) {
            if ("MIXED".equals(mode.path("code").asText())) {
                mixedModeTotal = mode.path("availableByDifficulty").path("MIXED").asInt();
            }
        }
        assertThat(mixedModeTotal).isEqualTo((int) realTotal).isGreaterThan(0);
    }

    // ================================================================
    // Starting a session
    // ================================================================

    @Test
    @DisplayName("starting persists a fixed unique order, hides reference answers, and is deterministic for a seed")
    void startPersistsFixedOrder() throws Exception {
        String body = postStart(student);
        JsonNode session = data(body);

        assertThat(session.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(session.path("questionCount").asInt()).isEqualTo(5);
        assertThat(session.path("answeredCount").asInt()).isZero();
        // The bank is descriptive only; the payload must not imply a score.
        assertThat(session.path("hasObjectiveQuestions").asBoolean()).isFalse();
        // The clock is server-derived so a refresh keeps counting.
        assertThat(session.path("elapsedSeconds").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(session.path("questions").size()).isEqualTo(5);

        long sessionId = session.path("id").asLong();
        List<MockInterviewSessionQuestion> detail = rows(sessionId);

        assertThat(detail).hasSize(5);
        assertThat(detail.stream().map(MockInterviewSessionQuestion::getPosition).toList())
                .containsExactly(1, 2, 3, 4, 5);
        // Never the same question twice in one interview.
        assertThat(detail.stream().map(r -> r.getQuestion().getId()).collect(Collectors.toSet())).hasSize(5);

        for (JsonNode q : session.path("questions")) {
            assertThat(q.path("question").asText()).isNotBlank();
            assertThat(q.path("moduleCode").asText()).isNotBlank();
            assertThat(q.path("difficulty").asText()).isNotBlank();
            // Reference guidance stays hidden until the interview is finished:
            // the field is either pruned or explicitly null, never a real answer.
            assertThat(q.path("referenceAnswer").isMissingNode() || q.path("referenceAnswer").isNull())
                    .as("referenceAnswer must not be exposed during an interview")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the same seed draws the same question set")
    void seededSelectionIsReproducible() throws Exception {
        List<Long> first = rows(startSession(student)).stream()
                .map(r -> r.getQuestion().getId()).toList();

        mockMvc.perform(post("/api/mock-interviews/" + sessionRepository.findAll().get(0).getId() + "/abandon")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        List<Long> second = rows(startSession(student)).stream()
                .map(r -> r.getQuestion().getId()).toList();

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("questions are capped to what exists rather than repeated, and 1..20 is enforced")
    void questionCountIsCappedNotPadded() throws Exception {
        // Find a real module/difficulty slice that holds fewer than the 20 maximum,
        // so asking for 20 genuinely forces the cap. Computed from the live bank
        // rather than hard-coded, so the test does not rot when content changes.
        long smallestModuleId = 0;
        PrepDifficulty smallestDifficulty = null;
        int smallestAvailable = Integer.MAX_VALUE;
        for (var module : prepModuleRepository.findByActiveTrueOrderBySortOrderAsc()) {
            for (PrepDifficulty d : List.of(PrepDifficulty.values())) {
                int available = (int) prepQuestionRepository.countActiveForMockInterview(
                        List.of(module.getId()), List.of(d));
                if (available > 0 && available < smallestAvailable) {
                    smallestAvailable = available;
                    smallestModuleId = module.getId();
                    smallestDifficulty = d;
                }
            }
        }
        assertThat(smallestDifficulty).as("bank has a module/difficulty slice under the cap")
                .isNotNull();
        assertThat(smallestAvailable).isBetween(1, 19);

        // MIXED covers every module, so this narrow selection is always a valid one.
        Map<String, Object> p = new HashMap<>();
        p.put("interviewType", "MIXED");
        p.put("difficulty", smallestDifficulty.name());
        p.put("moduleIds", List.of(smallestModuleId));
        p.put("questionCount", 20);

        String body = mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(p)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long sessionId = data(body).path("id").asLong();
        List<MockInterviewSessionQuestion> detail = rows(sessionId);

        // Capped to the real supply, not padded with repeats.
        assertThat(detail).hasSize(smallestAvailable);
        assertThat(detail.stream().map(r -> r.getQuestion().getId()).collect(Collectors.toSet()))
                .hasSize(detail.size());
        // Only the narrowed module and difficulty are represented.
        assertThat(detail.stream().map(r -> r.getQuestion().getTopic().getModule().getId()).distinct())
                .containsExactly(smallestModuleId);
        assertThat(detail.stream().map(r -> r.getQuestion().getDifficulty()).distinct())
                .containsExactly(smallestDifficulty);
        // The stored count matches what was actually stored.
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getQuestionCount())
                .isEqualTo(detail.size());
        assertThat(data(body).path("questionCount").asInt()).isEqualTo(smallestAvailable);

        // Out-of-range counts are rejected outright.
        mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("interviewType", "TECHNICAL", "difficulty", "MIXED", "questionCount", 21))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("interviewType", "TECHNICAL", "difficulty", "MIXED", "questionCount", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("invalid mode, difficulty and out-of-mode modules are rejected")
    void invalidSelectionRejected() throws Exception {
        mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("interviewType", "NOT_A_MODE", "difficulty", "MIXED", "questionCount", 5))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("interviewType", "TECHNICAL", "difficulty", "TRICKY", "questionCount", 5))))
                .andExpect(status().isBadRequest());

        // PROJECT is only in MIXED, so it must not be accepted for TECHNICAL.
        long projectModuleId = prepModuleRepository.findByCode("PROJECT").orElseThrow().getId();
        mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("interviewType", "TECHNICAL", "difficulty", "MIXED",
                                "questionCount", 5, "moduleIds", List.of(projectModuleId)))))
                .andExpect(status().isBadRequest());

        // A module with no active questions cannot be silently padded.
        assertThat(sessionRepository.findAll()).isEmpty();
    }

    // ================================================================
    // One active session, resume, refresh safety
    // ================================================================

    @Test
    @DisplayName("only one interview in progress at a time; a second attempt is 409 and abandoning frees the slot")
    void oneActiveSessionOnly() throws Exception {
        long first = startSession(student);

        mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(startPayload())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("in progress")));

        mockMvc.perform(post("/api/mock-interviews/" + first + "/abandon")
                        .with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ABANDONED"));

        long second = startSession(student);
        assertThat(second).isNotEqualTo(first);

        // The abandoned attempt is retained as history, not deleted.
        assertThat(sessionRepository.findById(first)).isPresent();
        assertThat(sessionRepository.findById(first).orElseThrow().getStatus().name())
                .isEqualTo("ABANDONED");
    }

    @Test
    @DisplayName("a reload resumes the same session with the same order and creates nothing new")
    void sessionIsResumableAndStable() throws Exception {
        long sessionId = startSession(student);

        String first = mockMvc.perform(get("/api/mock-interviews/" + sessionId)
                        .with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/api/mock-interviews/" + sessionId)
                        .with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Long> orderOf = List.of(
                data(first).path("questions").findValues("questionId").stream()
                        .map(n -> n.asLong()).toArray(Long[]::new));
        assertThat(data(second).path("questions").findValues("questionId"))
                .isEqualTo(data(first).path("questions").findValues("questionId"));
        assertThat(orderOf).hasSize(5);

        mockMvc.perform(get("/api/mock-interviews/me/active").with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sessionId));

        // Looking at the active endpoint repeatedly must not spawn sessions.
        mockMvc.perform(get("/api/mock-interviews/me/active").with(user(student.getEmail())));
        mockMvc.perform(get("/api/mock-interviews/me/active").with(user(student.getEmail())));
        assertThat(sessionRepository.findByStudentProfileIdOrderByCreatedAtDesc(
                studentProfileId(student), org.springframework.data.domain.PageRequest.of(0, 50)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("no active interview is a normal empty state, not an error and not a new session")
    void noActiveSessionIsEmpty() throws Exception {
        mockMvc.perform(get("/api/mock-interviews/me/active").with(user(student.getEmail())))
                .andExpect(status().isOk());
        assertThat(sessionRepository.findAll()).isEmpty();

        long sessionId = startSession(student);
        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        mockMvc.perform(get("/api/mock-interviews/me/active").with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ================================================================
    // Answers
    // ================================================================

    @Test
    @DisplayName("an answer survives a reload, clears with an empty string, and locks on completion")
    void answerPersistsClearsAndLocks() throws Exception {
        long sessionId = startSession(student);
        long sqId = rows(sessionId).get(0).getId();
        long otherSqId = rows(sessionId).get(1).getId();

        String answer = "Normalization removes redundancy; 3NF removes transitive dependency.";
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", answer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered").value(true))
                .andExpect(jsonPath("$.data.studentAnswer").value(answer))
                .andExpect(jsonPath("$.data.answeredAt").isNotEmpty());

        mockMvc.perform(get("/api/mock-interviews/" + sessionId).with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.questions[0].studentAnswer").value(answer))
                .andExpect(jsonPath("$.data.answeredCount").value(1));

        // Blank clears it again.
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered").value(false));
        mockMvc.perform(get("/api/mock-interviews/" + sessionId).with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.answeredCount").value(0));

        // Missing body field is a validation error, not a silent clear.
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + otherSqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + otherSqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", "changed my mind"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an answer of 5001 characters is rejected and not stored; exactly 5000 is accepted")
    void answerLengthBoundary() throws Exception {
        long sessionId = startSession(student);
        long sqId = rows(sessionId).get(0).getId();

        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", "x".repeat(5001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("too long")));

        assertThat(rows(sessionId).get(0).getStudentAnswer()).isNull();

        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", "y".repeat(5000)))))
                .andExpect(status().isOk());
        assertThat(rows(sessionId).get(0).getStudentAnswer()).hasSize(5000);
    }

    @Test
    @DisplayName("only this session's own questions are addressable; a foreign question id cannot be answered")
    void questionInjectionRejected() throws Exception {
        long sessionId = startSession(student);
        List<MockInterviewSessionQuestion> detail = rows(sessionId);

        Set<Long> sessionQuestionIds = new HashSet<>();
        Set<Long> sessionPrepQuestionIds = new HashSet<>();
        for (MockInterviewSessionQuestion r : detail) {
            sessionQuestionIds.add(r.getId());
            sessionPrepQuestionIds.add(r.getQuestion().getId());
        }

        // A preparation question id that is neither one of this session's
        // questions nor coincidentally one of its session-question ids.
        List<Long> allPrepQuestionIds = new ArrayList<>();
        prepQuestionRepository.findAll().forEach(q -> allPrepQuestionIds.add(q.getId()));
        Long foreignId = allPrepQuestionIds.stream()
                .filter(id -> !sessionQuestionIds.contains(id) && !sessionPrepQuestionIds.contains(id))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(foreignId != null,
                "No spare question id to prove injection rejection");

        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + foreignId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", "should not stick"))))
                .andExpect(status().isNotFound());

        // A real session-question id belonging to a different session is refused too.
        long otherSession = startSessionFor(pr);
        long prRowId = rows(otherSession).get(0).getId();
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + prRowId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", "cross session"))))
                .andExpect(status().isNotFound());

        // Nothing leaked into either session.
        for (MockInterviewSessionQuestion r : rows(sessionId)) {
            assertThat(r.getStudentAnswer()).isNull();
        }
        assertThat(rows(sessionId)).hasSize(5);
    }

    private long startSessionFor(User caller) throws Exception {
        String body = mockMvc.perform(post("/api/mock-interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(caller.getEmail()))
                        .content(json(startPayload())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return data(body).path("id").asLong();
    }

    // ================================================================
    // Self assessment
    // ================================================================

    @Test
    @DisplayName("self rating is accepted during the interview, changed freely after completion, and validated")
    void selfRatingLifecycle() throws Exception {
        long sessionId = startSession(student);
        long sqId = rows(sessionId).get(0).getId();

        rate(sessionId, sqId, student, "PARTIALLY_CONFIDENT");
        rate(sessionId, sqId, student, "NEED_PRACTICE");
        mockMvc.perform(get("/api/mock-interviews/" + sessionId).with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.questions[0].selfRating").value("NEED_PRACTICE"));

        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        // Review is exactly where re-rating matters most.
        rate(sessionId, sqId, student, "CONFIDENT");
        mockMvc.perform(get("/api/mock-interviews/" + sessionId).with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.questions[0].selfRating").value("CONFIDENT"));

        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/self-rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("selfRating", "VERY_CONFIDENT"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an abandoned interview closes its answers and self ratings")
    void abandonedSessionIsClosed() throws Exception {
        long sessionId = startSession(student);
        long sqId = rows(sessionId).get(0).getId();

        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/abandon")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("studentAnswer", "too late"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/mock-interviews/" + sessionId + "/questions/" + sqId + "/self-rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(student.getEmail()))
                        .content(json(Map.of("selfRating", "CONFIDENT"))))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // Completion, results, history
    // ================================================================

    @Test
    @DisplayName("completion records a terminal status and duration, and cannot be repeated")
    void completionIsTerminal() throws Exception {
        long completed = startSession(student);

        mockMvc.perform(post("/api/mock-interviews/" + completed + "/complete")
                        .with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.durationSeconds").isNumber());

        mockMvc.perform(post("/api/mock-interviews/" + completed + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mock-interviews/" + completed + "/abandon")
                .with(user(student.getEmail()))).andExpect(status().isBadRequest());

        long next = startSession(student);
        mockMvc.perform(post("/api/mock-interviews/" + next + "/abandon")
                .with(user(student.getEmail()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/mock-interviews/" + next + "/abandon")
                .with(user(student.getEmail()))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mock-interviews/" + next + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("results reveal guidance and report honest coverage with no invented score")
    void resultsAfterCompletion() throws Exception {
        long sessionId = startSession(student);
        List<MockInterviewSessionQuestion> detail = rows(sessionId);

        answer(sessionId, detail.get(0).getId(), student, "Normalization removes redundancy.");
        answer(sessionId, detail.get(1).getId(), student, "B+ trees keep height logarithmic.");
        answer(sessionId, detail.get(2).getId(), student, "Indexes speed reads at a write cost.");
        rate(sessionId, detail.get(0).getId(), student, "CONFIDENT");
        rate(sessionId, detail.get(1).getId(), student, "NEED_PRACTICE");
        rate(sessionId, detail.get(2).getId(), student, "PARTIALLY_CONFIDENT");

        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/mock-interviews/" + sessionId + "/results")
                        .with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(5))
                .andExpect(jsonPath("$.data.answeredCount").value(3))
                .andExpect(jsonPath("$.data.unansweredCount").value(2))
                .andExpect(jsonPath("$.data.objectiveScoringSupported").value(false))
                .andExpect(jsonPath("$.data.moduleBreakdown").isNotEmpty())
                .andExpect(jsonPath("$.data.topicBreakdown").isNotEmpty())
                .andExpect(jsonPath("$.data.selfAssessment.confident").value(1))
                .andExpect(jsonPath("$.data.selfAssessment.partiallyConfident").value(1))
                .andExpect(jsonPath("$.data.selfAssessment.needPractice").value(1))
                .andExpect(jsonPath("$.data.selfAssessment.unrated").value(2))
                .andExpect(jsonPath("$.data.selfAssessment.ratedCount").value(3))
                .andExpect(jsonPath("$.data.areasToReview").isNotEmpty())
                // No aggregate correctness number anywhere in the payload.
                .andExpect(jsonPath("$.data.overallScore").doesNotExist())
                .andExpect(jsonPath("$.data.percentage").doesNotExist())
                .andExpect(jsonPath("$.data.score").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Module/topic counts must reconcile with the session.
        JsonNode result = data(body);
        int moduleTotal = 0;
        for (JsonNode m : result.path("moduleBreakdown")) {
            moduleTotal += m.path("questionCount").asInt();
            assertThat(m.path("answeredCount").asInt()).isLessThanOrEqualTo(m.path("questionCount").asInt());
        }
        assertThat(moduleTotal).isEqualTo(5);

        int topicTotal = 0;
        for (JsonNode t : result.path("topicBreakdown")) {
            topicTotal += t.path("questionCount").asInt();
        }
        assertThat(topicTotal).isEqualTo(5);

        // Reference guidance is now available for review.
        mockMvc.perform(get("/api/mock-interviews/" + sessionId).with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.questions[0].referenceAnswer").isNotEmpty())
                .andExpect(jsonPath("$.data.questions[0].referenceAnswer").isNotEmpty());

        // And the finished interview is no longer offered as resumable.
        mockMvc.perform(get("/api/mock-interviews/me/active").with(user(student.getEmail())))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("results are refused until the interview is completed")
    void resultsRequireCompletion() throws Exception {
        long sessionId = startSession(student);
        mockMvc.perform(get("/api/mock-interviews/" + sessionId + "/results")
                .with(user(student.getEmail()))).andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/abandon")
                .with(user(student.getEmail()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/mock-interviews/" + sessionId + "/results")
                .with(user(student.getEmail()))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("history is server-paginated, filterable, and never leaks another student's sessions")
    void historyPaginatesAndFilters() throws Exception {
        long a = startSession(student);
        mockMvc.perform(post("/api/mock-interviews/" + a + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isOk());
        long b = startSession(student);
        answer(b, rows(b).get(0).getId(), student, "one answer");
        mockMvc.perform(post("/api/mock-interviews/" + b + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isOk());
        long c = startSession(student);
        mockMvc.perform(post("/api/mock-interviews/" + c + "/abandon")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        mockMvc.perform(get("/api/mock-interviews/me").with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(3));

        mockMvc.perform(get("/api/mock-interviews/me").param("status", "COMPLETED")
                        .with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[?(@.status=='COMPLETED')]").isNotEmpty());

        mockMvc.perform(get("/api/mock-interviews/me").param("status", "ABANDONED")
                        .with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("ABANDONED"));

        // Paging is real, and answered counts survive it.
        mockMvc.perform(get("/api/mock-interviews/me").param("page", "0").param("size", "2")
                        .with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));
        mockMvc.perform(get("/api/mock-interviews/me").param("page", "1").param("size", "2")
                        .with(user(student.getEmail())))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true));

        // An impossible page is empty, not an error.
        mockMvc.perform(get("/api/mock-interviews/me").param("page", "9").param("size", "10")
                        .with(user(student.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // An unknown status is rejected rather than ignored.
        mockMvc.perform(get("/api/mock-interviews/me").param("status", "NOPE")
                        .with(user(student.getEmail())))
                .andExpect(status().isBadRequest());

        // Other students see none of it.
        mockMvc.perform(get("/api/mock-interviews/me").with(user(otherStudentUser.getEmail())))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/mock-interviews/me").with(user(pr.getEmail())))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("the preparation bank is reused, never copied or altered")
    void preparationBankIsUntouched() throws Exception {
        long questionsBefore = prepQuestionRepository.count();
        long modulesBefore = prepModuleRepository.count();
        List<Long> prepIdsBefore = prepQuestionRepository.findAll().stream()
                .map(q -> q.getId()).sorted().toList();

        long sessionId = startSession(student);
        answer(sessionId, rows(sessionId).get(0).getId(), student, "an answer");
        mockMvc.perform(post("/api/mock-interviews/" + sessionId + "/complete")
                .with(user(student.getEmail()))).andExpect(status().isOk());

        assertThat(prepQuestionRepository.count()).isEqualTo(questionsBefore);
        assertThat(prepModuleRepository.count()).isEqualTo(modulesBefore);
        assertThat(prepQuestionRepository.findAll().stream().map(q -> q.getId()).sorted().toList())
                .isEqualTo(prepIdsBefore);

        // The session references existing question ids rather than storing copies.
        assertThat(rows(sessionId)).allSatisfy(r -> assertThat(prepIdsBefore).contains(r.getQuestion().getId()));
    }
}
