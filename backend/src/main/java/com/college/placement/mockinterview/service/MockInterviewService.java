package com.college.placement.mockinterview.service;

import com.college.placement.common.dto.PaginatedResponse;
import com.college.placement.common.enums.*;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.mockinterview.domain.MockInterviewSession;
import com.college.placement.mockinterview.domain.MockInterviewSessionQuestion;
import com.college.placement.mockinterview.dto.*;
import com.college.placement.mockinterview.repository.MockInterviewSessionQuestionRepository;
import com.college.placement.mockinterview.repository.MockInterviewSessionRepository;
import com.college.placement.preparation.domain.PrepModule;
import com.college.placement.preparation.domain.PrepQuestion;
import com.college.placement.preparation.repository.PrepModuleRepository;
import com.college.placement.preparation.repository.PrepQuestionRepository;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Mock Interview sessions, built on the existing Interview Preparation bank.
 *
 * <p>Honesty constraints that shape this class:
 * <ul>
 *   <li>The preparation bank is entirely descriptive — {@code prep_questions} has
 *       no question type, options or correct-answer column. Nothing here invents
 *       one, and no correctness percentage is ever produced.</li>
 *   <li>Every question carries a real, non-null {@code answer_guide}, which is
 *       surfaced as reference guidance only after the interview is finished.</li>
 *   <li>Difficulty is genuine authored metadata ({@link PrepDifficulty}), so the
 *       difficulty filter is exposed rather than fabricated.</li>
 *   <li>Questions are referenced by id. No question text is copied.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MockInterviewService {

    /** Question counts the setup screen may offer. */
    private static final List<Integer> ALLOWED_QUESTION_COUNTS = List.of(5, 10, 15, 20);
    private static final int MAX_QUESTION_COUNT = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int REVIEW_AREA_LIMIT = 5;

    private final MockInterviewSessionRepository sessionRepository;
    private final MockInterviewSessionQuestionRepository sessionQuestionRepository;
    private final PrepQuestionRepository prepQuestionRepository;
    private final PrepModuleRepository prepModuleRepository;
    private final StudentProfileRepository profileRepository;
    private final SecurityUtils securityUtils;
    private final MockInterviewModeCatalog modeCatalog;

    // =====================================================================
    // Options
    // =====================================================================

    @Transactional(readOnly = true)
    public MockInterviewOptionsResponse getOptions() {
        // Enforced here rather than only in the controller, so the rule holds for
        // every caller: Mock Interview is a student/PR surface only.
        requireStudentProfile();

        List<PrepModule> activeModules = prepModuleRepository.findByActiveTrueOrderBySortOrderAsc();

        // One grouped count query, then bucket in memory: no per-module queries.
        Map<Long, Long> totalByModule = countByModule(prepQuestionRepository.countActiveGroupedByModule());
        Map<ModuleDifficultyKey, Long> byModuleAndDifficulty =
                countByModuleAndDifficulty(prepQuestionRepository.countActiveGroupedByModuleAndDifficulty());

        List<MockInterviewModeOption> modes = new ArrayList<>();
        for (Map.Entry<MockInterviewType, MockInterviewModeCatalog.ModeInfo> entry
                : modeCatalog.allModes().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().name()))
                .toList()) {

            MockInterviewType type = entry.getKey();
            MockInterviewModeCatalog.ModeInfo info = entry.getValue();
            Set<String> wantedCodes = modeCatalog.moduleCodesFor(type);

            List<MockInterviewModuleOption> moduleOptions = activeModules.stream()
                    .filter(m -> wantedCodes.contains(m.getCode()))
                    .map(m -> MockInterviewModuleOption.builder()
                            .id(m.getId())
                            .code(m.getCode())
                            .title(m.getTitle())
                            .questionCount(totalByModule.getOrDefault(m.getId(), 0L).intValue())
                            .build())
                    .toList();

            Map<String, Integer> available = new LinkedHashMap<>();
            long modeTotal = 0;
            for (PrepDifficulty d : PrepDifficulty.values()) {
                int count = 0;
                for (MockInterviewModuleOption mo : moduleOptions) {
                    count += byModuleAndDifficulty
                            .getOrDefault(new ModuleDifficultyKey(mo.getId(), d), 0L).intValue();
                }
                available.put(d.name(), count);
                modeTotal += count;
            }
            available.put(MockDifficultyFilter.MIXED.name(), (int) modeTotal);

            modes.add(MockInterviewModeOption.builder()
                    .code(type.name())
                    .label(info.label())
                    .description(info.description())
                    .modules(moduleOptions)
                    .availableByDifficulty(available)
                    .build());
        }

        return MockInterviewOptionsResponse.builder()
                .modes(modes)
                .allowedQuestionCounts(ALLOWED_QUESTION_COUNTS)
                .maxAnswerLength(MockInterviewSessionQuestion.MAX_ANSWER_LENGTH)
                .objectiveScoringSupported(false)
                .build();
    }

    // =====================================================================
    // Start
    // =====================================================================

    @Transactional
    public MockInterviewSessionResponse startSession(StartMockInterviewRequest request) {
        StudentProfile profile = requireStudentProfile();

        MockInterviewType type = parseEnum(MockInterviewType.class, request.getInterviewType(), "interviewType");
        MockDifficultyFilter difficulty = parseEnum(MockDifficultyFilter.class, request.getDifficulty(), "difficulty");

        int requested = request.getQuestionCount() == null ? 0 : request.getQuestionCount();
        if (requested < 1 || requested > MAX_QUESTION_COUNT) {
            throw new BadRequestException("questionCount must be between 1 and " + MAX_QUESTION_COUNT + ".");
        }

        List<Long> moduleIds = resolveModuleIds(type, request.getModuleIds());
        Set<PrepDifficulty> difficulties = toPrepDifficulties(difficulty);

        // One query: how many genuinely exist for this filter.
        long available = prepQuestionRepository.countActiveForMockInterview(moduleIds, difficulties);
        if (available == 0) {
            throw new BadRequestException(
                    "No preparation questions match this selection. Try a different module or difficulty.");
        }

        // Never pad by repeating questions: cap to what actually exists.
        int actual = (int) Math.min(requested, available);

        // Preflight the one-active-session rule for a clear message; the partial
        // unique index is still the authority and is handled below.
        sessionRepository
                .findFirstByStudentProfileIdAndStatusOrderByStartedAtDesc(
                        profile.getId(), MockInterviewStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "You already have an interview in progress (session " + existing.getId() + "). "
                                    + "Resume it or abandon it before starting a new one.");
                });

        List<PrepQuestion> selected = selectQuestions(profile, moduleIds, difficulties, actual, request.getRandomSeed());

        MockInterviewSession session = MockInterviewSession.builder()
                .studentProfile(profile)
                .interviewType(type)
                .difficulty(difficulty)
                .questionCount(selected.size())
                .status(MockInterviewStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        List<MockInterviewSessionQuestion> rows = new ArrayList<>(selected.size());
        int position = 1;
        for (PrepQuestion question : selected) {
            rows.add(MockInterviewSessionQuestion.builder()
                    .session(session)
                    .question(question)
                    .position(position++)
                    .build());
        }

        try {
            session = sessionRepository.save(session);
            // Batch insert of the whole fixed order in one statement.
            sessionQuestionRepository.saveAll(rows);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "You already have an interview in progress. Resume it or abandon it before starting a new one.");
        }

        return toSessionResponse(session, rows);
    }

    // =====================================================================
    // Resume / detail
    // =====================================================================

    @Transactional(readOnly = true)
    public MockInterviewSessionResponse getActiveSession() {
        StudentProfile profile = requireStudentProfile();
        return sessionRepository
                .findFirstByStudentProfileIdAndStatusOrderByStartedAtDesc(
                        profile.getId(), MockInterviewStatus.IN_PROGRESS)
                .map(session -> toSessionResponse(session, sessionQuestionRepository.findSessionDetail(session.getId())))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MockInterviewSessionResponse getSession(Long sessionId) {
        StudentProfile profile = requireStudentProfile();
        MockInterviewSession session = requireOwnedSession(sessionId, profile);
        return toSessionResponse(session, sessionQuestionRepository.findSessionDetail(sessionId));
    }

    // =====================================================================
    // Answering
    // =====================================================================

    @Transactional
    public MockInterviewQuestionView saveAnswer(Long sessionId, Long sessionQuestionId, String studentAnswer) {
        StudentProfile profile = requireStudentProfile();
        MockInterviewSession session = requireOwnedSession(sessionId, profile);

        if (!session.isInProgress()) {
            throw new BadRequestException(
                    "This interview is " + session.getStatus().name()
                            + " and its answers can no longer be changed.");
        }

        MockInterviewSessionQuestion row = sessionQuestionRepository
                .findOwnedSessionQuestion(sessionQuestionId, sessionId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Mock interview question", sessionQuestionId));

        if (studentAnswer != null && studentAnswer.length() > MockInterviewSessionQuestion.MAX_ANSWER_LENGTH) {
            throw new BadRequestException(
                    "Answer is too long. Maximum " + MockInterviewSessionQuestion.MAX_ANSWER_LENGTH
                            + " characters (received " + studentAnswer.length() + ").");
        }

        String normalised = (studentAnswer == null || studentAnswer.isBlank()) ? null : studentAnswer;
        row.setStudentAnswer(normalised);
        row.setAnsweredAt(normalised == null ? null : LocalDateTime.now());
        sessionQuestionRepository.save(row);

        return toQuestionView(row, session.isInProgress());
    }

    @Transactional
    public MockInterviewQuestionView saveSelfRating(Long sessionId, Long sessionQuestionId, String selfRating) {
        StudentProfile profile = requireStudentProfile();
        MockInterviewSession session = requireOwnedSession(sessionId, profile);

        if (session.getStatus() == MockInterviewStatus.ABANDONED) {
            throw new BadRequestException("This interview was abandoned; its answers are closed.");
        }

        MockInterviewSessionQuestion row = sessionQuestionRepository
                .findOwnedSessionQuestion(sessionQuestionId, sessionId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Mock interview question", sessionQuestionId));

        row.setSelfRating(isBlank(selfRating) ? null
                : parseEnum(MockSelfRating.class, selfRating, "selfRating"));
        sessionQuestionRepository.save(row);

        return toQuestionView(row, session.isInProgress());
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Transactional
    public MockInterviewSummaryResponse completeSession(Long sessionId) {
        StudentProfile profile = requireStudentProfile();
        MockInterviewSession session = requireOwnedSession(sessionId, profile);

        if (!session.isInProgress()) {
            throw new BadRequestException(
                    "This interview is already " + session.getStatus().name() + ".");
        }

        LocalDateTime now = LocalDateTime.now();
        session.setStatus(MockInterviewStatus.COMPLETED);
        session.setCompletedAt(now);
        session.setDurationSeconds(elapsedSeconds(session, now));
        sessionRepository.save(session);

        return toSummary(session, countAnswered(session.getId()));
    }

    @Transactional
    public MockInterviewSummaryResponse abandonSession(Long sessionId) {
        StudentProfile profile = requireStudentProfile();
        MockInterviewSession session = requireOwnedSession(sessionId, profile);

        if (!session.isInProgress()) {
            throw new BadRequestException(
                    "This interview is already " + session.getStatus().name() + ".");
        }

        LocalDateTime now = LocalDateTime.now();
        session.setStatus(MockInterviewStatus.ABANDONED);
        session.setCompletedAt(now);
        session.setDurationSeconds(elapsedSeconds(session, now));
        sessionRepository.save(session);

        return toSummary(session, countAnswered(session.getId()));
    }

    // =====================================================================
    // History and results
    // =====================================================================

    @Transactional(readOnly = true)
    public PaginatedResponse<MockInterviewSummaryResponse> getHistory(int page, int size, String status) {
        StudentProfile profile = requireStudentProfile();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<MockInterviewSession> result;
        if (isBlank(status)) {
            result = sessionRepository.findByStudentProfileIdOrderByCreatedAtDesc(profile.getId(), pageable);
        } else {
            MockInterviewStatus parsed = parseEnum(MockInterviewStatus.class, status, "status");
            result = sessionRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDesc(
                    profile.getId(), parsed, pageable);
        }

        // One grouped query for the whole page, not one per row.
        Map<Long, Long> answeredBySession = answeredCounts(result.getContent());

        List<MockInterviewSummaryResponse> content = result.getContent().stream()
                .map(s -> toSummary(s, answeredBySession.getOrDefault(s.getId(), 0L).intValue()))
                .toList();

        return PaginatedResponse.<MockInterviewSummaryResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public MockInterviewResultResponse getResult(Long sessionId) {
        StudentProfile profile = requireStudentProfile();
        MockInterviewSession session = requireOwnedSession(sessionId, profile);

        if (session.getStatus() != MockInterviewStatus.COMPLETED) {
            throw new BadRequestException(
                    "Results are available once the interview is completed. This one is "
                            + session.getStatus().name() + ".");
        }

        List<MockInterviewSessionQuestion> rows = sessionQuestionRepository.findSessionDetail(sessionId);

        // ---- module breakdown
        Map<Long, List<MockInterviewSessionQuestion>> byModule = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getQuestion().getTopic().getModule().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        List<MockInterviewModuleBreakdown> moduleBreakdown = byModule.entrySet().stream()
                .map(e -> {
                    PrepModule m = e.getValue().get(0).getQuestion().getTopic().getModule();
                    return MockInterviewModuleBreakdown.builder()
                            .moduleId(m.getId())
                            .moduleCode(m.getCode())
                            .moduleTitle(m.getTitle())
                            .questionCount(e.getValue().size())
                            .answeredCount((int) e.getValue().stream().filter(MockInterviewSessionQuestion::isAnswered).count())
                            .build();
                })
                .sorted(Comparator.comparing(MockInterviewModuleBreakdown::getQuestionCount).reversed())
                .toList();

        // ---- topic breakdown + need-practice counts
        Map<Long, List<MockInterviewSessionQuestion>> byTopic = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getQuestion().getTopic().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        List<MockInterviewTopicBreakdown> topicBreakdown = byTopic.entrySet().stream()
                .map(e -> {
                    var topic = e.getValue().get(0).getQuestion().getTopic();
                    return MockInterviewTopicBreakdown.builder()
                            .topicId(topic.getId())
                            .topicCode(topic.getCode())
                            .topicTitle(topic.getTitle())
                            .moduleCode(topic.getModule().getCode())
                            .questionCount(e.getValue().size())
                            .answeredCount((int) e.getValue().stream().filter(MockInterviewSessionQuestion::isAnswered).count())
                            .needPracticeCount((int) e.getValue().stream()
                                    .filter(r -> r.getSelfRating() == MockSelfRating.NEED_PRACTICE).count())
                            .build();
                })
                .sorted(Comparator.comparing(MockInterviewTopicBreakdown::getQuestionCount).reversed())
                .toList();

        // ---- self assessment tally (the student's own judgement, not a score)
        int confident = (int) rows.stream().filter(r -> r.getSelfRating() == MockSelfRating.CONFIDENT).count();
        int partial = (int) rows.stream().filter(r -> r.getSelfRating() == MockSelfRating.PARTIALLY_CONFIDENT).count();
        int needPractice = (int) rows.stream().filter(r -> r.getSelfRating() == MockSelfRating.NEED_PRACTICE).count();
        int unrated = (int) rows.stream().filter(r -> r.getSelfRating() == null).count();

        // ---- areas to review: only from real signals
        List<MockInterviewReviewArea> areasToReview = topicBreakdown.stream()
                .filter(t -> t.getNeedPracticeCount() > 0
                        || t.getQuestionCount() - t.getAnsweredCount() > 0)
                .map(t -> MockInterviewReviewArea.builder()
                        .moduleCode(t.getModuleCode())
                        .topicTitle(t.getTopicTitle())
                        .reason(reviewReason(t))
                        .needPracticeCount(t.getNeedPracticeCount())
                        .unansweredCount(t.getQuestionCount() - t.getAnsweredCount())
                        .build())
                .sorted(Comparator.comparingInt((MockInterviewReviewArea a) ->
                        a.getNeedPracticeCount() + a.getUnansweredCount()).reversed())
                .limit(REVIEW_AREA_LIMIT)
                .toList();

        int answered = (int) rows.stream().filter(MockInterviewSessionQuestion::isAnswered).count();

        return MockInterviewResultResponse.builder()
                .id(session.getId())
                .interviewType(session.getInterviewType().name())
                .interviewTypeLabel(modeCatalog.label(session.getInterviewType()))
                .difficulty(session.getDifficulty().name())
                .status(session.getStatus().name())
                .questionCount(session.getQuestionCount())
                .answeredCount(answered)
                .unansweredCount(session.getQuestionCount() - answered)
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .durationSeconds(session.getDurationSeconds())
                .objectiveScoringSupported(false)
                .moduleBreakdown(moduleBreakdown)
                .topicBreakdown(topicBreakdown)
                .selfAssessment(MockInterviewSelfAssessmentSummary.builder()
                        .confident(confident)
                        .partiallyConfident(partial)
                        .needPractice(needPractice)
                        .unrated(unrated)
                        .ratedCount(confident + partial + needPractice)
                        .build())
                .areasToReview(areasToReview)
                .build();
    }

    // =====================================================================
    // Internals
    // =====================================================================

    /**
     * Draws the question set.
     *
     * <p>Questions the student has not seen in a non-abandoned session are
     * preferred, with previously seen ones filling any remainder — so a narrow
     * topic with few questions still works. Ordering is fixed and persisted by
     * the caller, so this randomness is spent exactly once per session.
     *
     * <p>Two queries total, independent of how many questions are requested.
     */
    private List<PrepQuestion> selectQuestions(StudentProfile profile,
                                              List<Long> moduleIds,
                                              Set<PrepDifficulty> difficulties,
                                              int count,
                                              Long seed) {
        List<PrepQuestion> candidates =
                prepQuestionRepository.findActiveForMockInterview(moduleIds, difficulties);
        if (candidates.isEmpty()) {
            throw new BadRequestException(
                    "No preparation questions match this selection. Try a different module or difficulty.");
        }

        Set<Long> seen = new HashSet<>(sessionQuestionRepository.findQuestionIdsSeenByStudent(
                profile.getId(), MockInterviewStatus.ABANDONED));

        List<PrepQuestion> unseen = new ArrayList<>();
        List<PrepQuestion> revisited = new ArrayList<>();
        for (PrepQuestion q : candidates) {
            if (seen.contains(q.getId())) {
                revisited.add(q);
            } else {
                unseen.add(q);
            }
        }

        Random random = seed == null ? ThreadLocalRandom.current() : new Random(seed);
        Collections.shuffle(unseen, random);
        Collections.shuffle(revisited, random);

        List<PrepQuestion> selected = new ArrayList<>(count);
        for (PrepQuestion q : unseen) {
            if (selected.size() == count) break;
            selected.add(q);
        }
        for (PrepQuestion q : revisited) {
            if (selected.size() == count) break;
            selected.add(q);
        }
        return selected;
    }

    private List<Long> resolveModuleIds(MockInterviewType type, List<Long> requested) {
        Set<String> wantedCodes = modeCatalog.moduleCodesFor(type);

        List<PrepModule> modeModules = prepModuleRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .filter(m -> wantedCodes.contains(m.getCode()))
                .toList();

        if (modeModules.isEmpty()) {
            throw new BadRequestException(
                    "No active preparation content is available for " + modeCatalog.label(type) + ".");
        }

        if (requested == null || requested.isEmpty()) {
            return modeModules.stream().map(PrepModule::getId).toList();
        }

        Set<Long> allowed = modeModules.stream().map(PrepModule::getId).collect(Collectors.toSet());
        List<Long> requestedDistinct = requested.stream().filter(Objects::nonNull).distinct().toList();

        if (requestedDistinct.isEmpty()) {
            throw new BadRequestException("moduleIds must contain at least one id.");
        }
        for (Long id : requestedDistinct) {
            if (!allowed.contains(id)) {
                throw new BadRequestException(
                        "Module " + id + " is not part of " + modeCatalog.label(type) + ".");
            }
        }
        return requestedDistinct;
    }

    private StudentProfile requireStudentProfile() {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser == null
                || (currentUser.getRole() != Role.STUDENT && currentUser.getRole() != Role.PR)) {
            throw new ForbiddenException(
                    "Mock Interview is available only to students and PRs linked to a student profile.");
        }
        return profileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ForbiddenException(
                        "No student profile is linked to this account."));
    }

    /**
     * Ownership-scoped load. A session owned by another student is reported as
     * not found, which both satisfies the access rule and avoids confirming that
     * someone else's session exists.
     */
    private MockInterviewSession requireOwnedSession(Long sessionId, StudentProfile profile) {
        return sessionRepository.findOwnedSession(sessionId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Mock interview session", sessionId));
    }

    private static Set<PrepDifficulty> toPrepDifficulties(MockDifficultyFilter filter) {
        if (filter == MockDifficultyFilter.MIXED) {
            return EnumSet.allOf(PrepDifficulty.class);
        }
        return EnumSet.of(PrepDifficulty.valueOf(filter.name()));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (isBlank(raw)) {
            throw new BadRequestException(field + " is required.");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Invalid " + field + " '" + raw + "'. Allowed: " + Arrays.toString(type.getEnumConstants()) + ".");
        }
    }

    private static String reviewReason(MockInterviewTopicBreakdown t) {
        boolean need = t.getNeedPracticeCount() > 0;
        boolean skipped = t.getQuestionCount() - t.getAnsweredCount() > 0;
        if (need && skipped) return "Marked as needing practice and left partly unanswered";
        if (need) return "Marked as needing practice";
        return "Left unanswered";
    }

    private static int elapsedSeconds(MockInterviewSession session, LocalDateTime now) {
        if (session.getStartedAt() == null) {
            return 0;
        }
        long seconds = Duration.between(session.getStartedAt(), now).getSeconds();
        return (int) Math.max(seconds, 0);
    }

    private int countAnswered(Long sessionId) {
        return sessionQuestionRepository.findBySessionIdOrderByPosition(sessionId).stream()
                .filter(MockInterviewSessionQuestion::isAnswered)
                .toList()
                .size();
    }

    private Map<Long, Long> answeredCounts(List<MockInterviewSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : sessionQuestionRepository.countAnsweredBySessionIds(
                sessions.stream().map(MockInterviewSession::getId).toList())) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static Map<Long, Long> countByModule(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static Map<ModuleDifficultyKey, Long> countByModuleAndDifficulty(List<Object[]> rows) {
        Map<ModuleDifficultyKey, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(new ModuleDifficultyKey(((Number) row[0]).longValue(), (PrepDifficulty) row[1]),
                    ((Number) row[2]).longValue());
        }
        return map;
    }

    private record ModuleDifficultyKey(Long moduleId, PrepDifficulty difficulty) {
    }

    // ---- mapping -------------------------------------------------------

    private MockInterviewSessionResponse toSessionResponse(MockInterviewSession session, List<MockInterviewSessionQuestion> rows) {
        boolean revealAnswers = session.getStatus() == MockInterviewStatus.COMPLETED;
        List<MockInterviewQuestionView> questions = rows.stream()
                .map(r -> toQuestionView(r, !revealAnswers))
                .toList();

        int answered = (int) rows.stream().filter(MockInterviewSessionQuestion::isAnswered).count();

        return MockInterviewSessionResponse.builder()
                .id(session.getId())
                .interviewType(session.getInterviewType().name())
                .interviewTypeLabel(modeCatalog.label(session.getInterviewType()))
                .difficulty(session.getDifficulty().name())
                .status(session.getStatus().name())
                .questionCount(session.getQuestionCount())
                .answeredCount(answered)
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .durationSeconds(session.getDurationSeconds())
                .elapsedSeconds(session.getStatus() == MockInterviewStatus.IN_PROGRESS
                        ? (long) elapsedSeconds(session, LocalDateTime.now())
                        : (long) Optional.ofNullable(session.getDurationSeconds()).orElse(0))
                .hasObjectiveQuestions(false)
                .questions(questions)
                .build();
    }

    private MockInterviewQuestionView toQuestionView(MockInterviewSessionQuestion row, boolean hideReference) {
        PrepQuestion q = row.getQuestion();
        var topic = q.getTopic();
        var module = topic.getModule();

        return MockInterviewQuestionView.builder()
                .sessionQuestionId(row.getId())
                .position(row.getPosition())
                .questionId(q.getId())
                .question(q.getQuestion())
                .difficulty(q.getDifficulty().name())
                .moduleId(module.getId())
                .moduleCode(module.getCode())
                .moduleTitle(module.getTitle())
                .topicId(topic.getId())
                .topicTitle(topic.getTitle())
                .studentAnswer(row.getStudentAnswer())
                .selfRating(row.getSelfRating() != null ? row.getSelfRating().name() : null)
                .answered(row.isAnswered())
                .answeredAt(row.getAnsweredAt())
                .referenceAnswer(hideReference || isBlank(q.getAnswerGuide()) ? null : q.getAnswerGuide())
                .build();
    }

    private MockInterviewSummaryResponse toSummary(MockInterviewSession session, int answeredCount) {
        return MockInterviewSummaryResponse.builder()
                .id(session.getId())
                .interviewType(session.getInterviewType().name())
                .interviewTypeLabel(modeCatalog.label(session.getInterviewType()))
                .difficulty(session.getDifficulty().name())
                .status(session.getStatus().name())
                .questionCount(session.getQuestionCount())
                .answeredCount(answeredCount)
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .durationSeconds(session.getDurationSeconds())
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
