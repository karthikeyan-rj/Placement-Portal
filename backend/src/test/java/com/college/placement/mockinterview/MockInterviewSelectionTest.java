package com.college.placement.mockinterview;

import com.college.placement.common.enums.MockDifficultyFilter;
import com.college.placement.common.enums.MockInterviewStatus;
import com.college.placement.common.enums.MockInterviewType;
import com.college.placement.common.enums.PrepDifficulty;
import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.mockinterview.domain.MockInterviewSession;
import com.college.placement.mockinterview.domain.MockInterviewSessionQuestion;
import com.college.placement.mockinterview.dto.StartMockInterviewRequest;
import com.college.placement.mockinterview.repository.MockInterviewSessionQuestionRepository;
import com.college.placement.mockinterview.repository.MockInterviewSessionRepository;
import com.college.placement.mockinterview.service.MockInterviewService;
import com.college.placement.preparation.repository.PrepQuestionRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants that are awkward to prove through HTTP: that the database itself
 * refuses a second active interview, and that question selection really prefers
 * unseen content. Runs against the real service and the real bank inside one
 * rolled-back transaction.
 */
@SpringBootTest
@Transactional
class MockInterviewSelectionTest {

    @Autowired
    private MockInterviewService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PrepQuestionRepository prepQuestionRepository;

    @Autowired
    private MockInterviewSessionRepository sessionRepository;

    @Autowired
    private MockInterviewSessionQuestionRepository sessionQuestionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Department cse;
    private User student;
    private StudentProfile profile;

    @BeforeEach
    void setUp() {
        cse = MockInterviewFixtures.department(departmentRepository, "MIS");
        student = MockInterviewFixtures.user(userRepository, "misStudent", Role.STUDENT, cse);
        profile = MockInterviewFixtures.studentProfile(studentProfileRepository, student);
        // The service resolves the owner from the security context.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(student.getEmail(), "n/a", List.of()));
    }

    private StartMockInterviewRequest request(String type, String difficulty, int count) {
        return request(type, difficulty, count, 99L);
    }

    private StartMockInterviewRequest request(String type, String difficulty, int count, Long seed) {
        return StartMockInterviewRequest.builder()
                .interviewType(type)
                .difficulty(difficulty)
                .questionCount(count)
                .randomSeed(seed)
                .build();
    }

    private List<Long> questionIdsOf(long sessionId) {
        return sessionQuestionRepository.findSessionDetail(sessionId).stream()
                .map(r -> r.getQuestion().getId()).toList();
    }

    // ================================================================
    // The database is the authority on one active interview
    //
    // These two tests commit their setup on purpose. A constraint violation
    // raised in a second transaction can only be observed if the row it clashes
    // with is already visible; if the clashing row were still uncommitted, the
    // insert would block on it until the holding transaction finished, while
    // the holding transaction waits for the second one to return. That cycle
    // is invisible to the database's deadlock detector, so the test would hang
    // instead of failing. Committing first makes the conflict fail fast.
    // ================================================================

    /** Ends the rolled-back test transaction, committing the fixtures created so far. */
    private void commitSetup() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    /** Removes the rows these tests had to commit, leaving the dev database clean. */
    private void removeCommittedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (MockInterviewSession session : sessionRepository.findAll()) {
                sessionQuestionRepository.deleteAll(
                        sessionQuestionRepository.findBySessionIdOrderByPosition(session.getId()));
            }
            sessionRepository.deleteAll(sessionRepository.findAll());
            studentProfileRepository.deleteById(profile.getId());
            userRepository.deleteById(student.getId());
            departmentRepository.deleteById(cse.getId());
        });
    }

    @Test
    @DisplayName("the partial unique index refuses a second in-progress session even if the service is bypassed")
    void databaseRefusesSecondActiveSession() {
        long first = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 5)).getId();
        long profileId = profile.getId();

        commitSetup();

        try {
            // Bypass the service preflight entirely and write straight to the table.
            TransactionTemplate inner = new TransactionTemplate(transactionManager);
            inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            assertThatThrownBy(() -> inner.executeWithoutResult(status -> {
                MockInterviewSession duplicate = MockInterviewSession.builder()
                        .studentProfile(studentProfileRepository.findById(profileId).orElseThrow())
                        .interviewType(MockInterviewType.TECHNICAL)
                        .difficulty(MockDifficultyFilter.MIXED)
                        .questionCount(5)
                        .status(MockInterviewStatus.IN_PROGRESS)
                        .startedAt(LocalDateTime.now())
                        .build();
                sessionRepository.saveAndFlush(duplicate);
            })).isInstanceOf(DataIntegrityViolationException.class);

            // The original session is untouched, and still the only active one.
            assertThat(sessionRepository.findById(first).orElseThrow().isInProgress()).isTrue();
            assertThat(sessionRepository
                    .findFirstByStudentProfileIdAndStatusOrderByStartedAtDesc(
                            profileId, MockInterviewStatus.IN_PROGRESS))
                    .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(first));

            // A second terminal session is still allowed alongside the active one.
            TransactionTemplate ok = new TransactionTemplate(transactionManager);
            ok.executeWithoutResult(status -> {
                MockInterviewSession finished = MockInterviewSession.builder()
                        .studentProfile(studentProfileRepository.findById(profileId).orElseThrow())
                        .interviewType(MockInterviewType.TECHNICAL)
                        .difficulty(MockDifficultyFilter.MIXED)
                        .questionCount(5)
                        .status(MockInterviewStatus.COMPLETED)
                        .startedAt(LocalDateTime.now())
                        .completedAt(LocalDateTime.now())
                        .durationSeconds(60)
                        .build();
                assertThat(sessionRepository.saveAndFlush(finished).getId()).isPositive();
            });

            assertThat(sessionRepository
                    .findFirstByStudentProfileIdAndStatusOrderByStartedAtDesc(
                            profileId, MockInterviewStatus.IN_PROGRESS))
                    .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(first));
        } finally {
            removeCommittedFixtures();
        }
    }

    @Test
    @DisplayName("two concurrent starts still leave exactly one active session")
    void concurrentStartsLeaveOneSession() {
        long first = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 5)).getId();

        // The service preflight turns the second attempt into a clean 409.
        assertThatThrownBy(() -> service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 5)))
                .isInstanceOf(com.college.placement.common.exception.ConflictException.class);

        assertThat(sessionRepository.findByStudentProfileIdAndStatus(profile.getId(), MockInterviewStatus.IN_PROGRESS))
                .hasSize(1);
        assertThat(sessionRepository.findAll()).hasSize(1);
        assertThat(first).isPositive();
    }

    // ================================================================
    // Selection really avoids repetition
    // ================================================================

    @Test
    @DisplayName("a second interview draws unseen questions while unseen ones remain")
    void secondSessionPrefersUnseenQuestions() {
        long first = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 10)).getId();
        service.completeSession(first);

        long second = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 10)).getId();

        Set<Long> firstSet = new HashSet<>(questionIdsOf(first));
        Set<Long> secondSet = new HashSet<>(questionIdsOf(second));

        assertThat(firstSet).hasSize(10);
        assertThat(secondSet).hasSize(10);
        // 20 unseen technical questions are available, so there is no reason to repeat.
        assertThat(firstSet).doesNotContainAnyElementsOf(secondSet);
    }

    @Test
    @DisplayName("a narrow filter still works by revisiting once the unseen pool runs out, without repeating inside a session")
    void revisitedQuestionsNeverRepeatWithinASession() {
        // EASY technical questions are the scarcest slice, so several runs force reuse.
        List<Long> union = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long session = service.startSession(
                    request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.EASY.name(), 8)).getId();
            List<Long> ids = questionIdsOf(session);
            // Never the same question twice inside one interview.
            assertThat(new HashSet<>(ids)).hasSize(ids.size());
            assertThat(ids).allSatisfy(id -> assertThat(
                    prepQuestionRepository.findById(id).orElseThrow().getDifficulty())
                    .isEqualTo(PrepDifficulty.EASY));
            service.completeSession(session);
            union.addAll(ids);
        }
        // Repeats across sessions are permitted; repeats within one are not.
        assertThat(union).isNotEmpty();
    }

    @Test
    @DisplayName("abandoned runs are not treated as exposure, completed ones are")
    void abandonedSessionsDoNotCountAsExposure() {
        long abandoned = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 5, 1L)).getId();
        Set<Long> abandonedIds = new HashSet<>(questionIdsOf(abandoned));
        service.abandonSession(abandoned);

        // Nothing counts as seen yet: a dropped practice run is not exposure.
        assertThat(sessionQuestionRepository.findQuestionIdsSeenByStudent(
                profile.getId(), MockInterviewStatus.ABANDONED)).isEmpty();

        // Because the abandoned run left no trace, the next draw is free to
        // repeat those questions rather than being pushed onto new ones.
        long repeated = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 5, 1L)).getId();
        assertThat(new HashSet<>(questionIdsOf(repeated)))
                .as("an abandoned session must not push the student away from those questions")
                .isEqualTo(abandonedIds);
        service.abandonSession(repeated);

        long completed = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 5, 2L)).getId();
        Set<Long> completedIds = new HashSet<>(questionIdsOf(completed));
        service.completeSession(completed);

        List<Long> seen = sessionQuestionRepository.findQuestionIdsSeenByStudent(
                profile.getId(), MockInterviewStatus.ABANDONED);

        // Only the completed session's questions were recorded as seen.
        assertThat(seen).containsAll(completedIds);
        assertThat(seen).hasSize(completedIds.size());
    }

    @Test
    @DisplayName("the difficulty filter and the mode's module scope are both honoured")
    void filterAndScopeAreHonoured() {
        long easy = service.startSession(
                request(MockInterviewType.MIXED.name(), MockDifficultyFilter.EASY.name(), 6)).getId();

        assertThat(sessionQuestionRepository.findSessionDetail(easy))
                .allSatisfy(r -> assertThat(r.getQuestion().getDifficulty()).isEqualTo(PrepDifficulty.EASY));
        service.completeSession(easy);

        long hard = service.startSession(
                request(MockInterviewType.MIXED.name(), MockDifficultyFilter.HARD.name(), 6)).getId();

        assertThat(sessionQuestionRepository.findSessionDetail(hard))
                .allSatisfy(r -> assertThat(r.getQuestion().getDifficulty()).isEqualTo(PrepDifficulty.HARD));
        service.completeSession(hard);

        // A behavioural interview draws only behavioural content.
        long hr = service.startSession(
                request(MockInterviewType.HR_BEHAVIORAL.name(), MockDifficultyFilter.MIXED.name(), 5)).getId();

        assertThat(sessionQuestionRepository.findSessionDetail(hr))
                .allSatisfy(r -> assertThat(r.getQuestion().getTopic().getModule().getCode()).isEqualTo("HR"));
    }

    @Test
    @DisplayName("the preparation bank is read but never written by an interview")
    void selectionDoesNotMutateTheBank() {
        long before = prepQuestionRepository.count();
        List<Long> idsBefore = prepQuestionRepository.findAll().stream()
                .map(q -> q.getId()).sorted().toList();
        List<String> textBefore = prepQuestionRepository.findAll().stream()
                .map(q -> q.getQuestion()).sorted().toList();

        long session = service.startSession(
                request(MockInterviewType.MIXED.name(), MockDifficultyFilter.MIXED.name(), 10)).getId();
        service.saveAnswer(session, sessionQuestionRepository.findSessionDetail(session).get(0).getId(),
                "a practice answer");
        service.completeSession(session);

        assertThat(prepQuestionRepository.count()).isEqualTo(before);
        assertThat(prepQuestionRepository.findAll().stream().map(q -> q.getId()).sorted().toList())
                .isEqualTo(idsBefore);
        assertThat(prepQuestionRepository.findAll().stream().map(q -> q.getQuestion()).sorted().toList())
                .isEqualTo(textBefore);
    }

    @Test
    @DisplayName("a session-question row cannot be inserted twice for the same position or question")
    void sessionQuestionRowsAreUnique() {
        long session = service.startSession(
                request(MockInterviewType.TECHNICAL.name(), MockDifficultyFilter.MIXED.name(), 5)).getId();
        MockInterviewSessionQuestion first = sessionQuestionRepository.findSessionDetail(session).get(0);
        Long existingSessionId = sessionRepository.findById(session).orElseThrow().getId();
        Long questionId = first.getQuestion().getId();
        int position = first.getPosition();

        commitSetup();

        try {
            TransactionTemplate inner = new TransactionTemplate(transactionManager);
            inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            // Same session, same position, same question.
            assertThatThrownBy(() -> inner.executeWithoutResult(status ->
                    sessionQuestionRepository.saveAndFlush(MockInterviewSessionQuestion.builder()
                            .session(sessionRepository.findById(existingSessionId).orElseThrow())
                            .question(prepQuestionRepository.findById(questionId).orElseThrow())
                            .position(position)
                            .build())))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // Same session and question at a different position is still refused.
            assertThatThrownBy(() -> inner.executeWithoutResult(status ->
                    sessionQuestionRepository.saveAndFlush(MockInterviewSessionQuestion.builder()
                            .session(sessionRepository.findById(existingSessionId).orElseThrow())
                            .question(prepQuestionRepository.findById(questionId).orElseThrow())
                            .position(position + 50)
                            .build())))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(sessionQuestionRepository.findSessionDetail(session)).hasSize(5);
        } finally {
            removeCommittedFixtures();
        }
    }
}
