package com.college.placement.preparation.service;

import com.college.placement.common.enums.PrepConfidence;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ForbiddenException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.preparation.domain.PrepModule;
import com.college.placement.preparation.domain.PrepQuestion;
import com.college.placement.preparation.domain.PrepTopic;
import com.college.placement.preparation.domain.StudentPrepProgress;
import com.college.placement.preparation.dto.*;
import com.college.placement.preparation.repository.PrepModuleRepository;
import com.college.placement.preparation.repository.PrepQuestionRepository;
import com.college.placement.preparation.repository.PrepTopicRepository;
import com.college.placement.preparation.repository.StudentPrepProgressRepository;
import com.college.placement.security.SecurityUtils;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreparationService {

    private final PrepModuleRepository moduleRepository;
    private final PrepTopicRepository topicRepository;
    private final PrepQuestionRepository questionRepository;
    private final StudentPrepProgressRepository progressRepository;
    private final StudentProfileRepository profileRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<PrepModuleResponse> getModuleList() {
        StudentProfile profile = currentStudentProfile().orElse(null);

        List<PrepModule> modules = moduleRepository.findByActiveTrueOrderBySortOrderAsc();
        Map<Long, Long> topicCounts = topicRepository.countActiveTopicsGroupedByModule().stream()
                .collect(Collectors.toMap(PrepTopicRepository.TopicCountProjection::getModuleId,
                        PrepTopicRepository.TopicCountProjection::getCnt));

        Map<Long, Long> completedByModule = new HashMap<>();
        if (profile != null) {
            for (StudentPrepProgress p : progressRepository.findByStudentProfileId(profile.getId())) {
                PrepTopic topic = p.getTopic();
                if (!Boolean.TRUE.equals(topic.getActive())
                        || !Boolean.TRUE.equals(topic.getModule().getActive())) {
                    continue;
                }
                if (Boolean.TRUE.equals(p.getCompleted())) {
                    completedByModule.merge(topic.getModule().getId(), 1L, Long::sum);
                }
            }
        }

        List<PrepModuleResponse> result = new ArrayList<>(modules.size());
        for (PrepModule module : modules) {
            long total = topicCounts.getOrDefault(module.getId(), 0L);
            long completed = completedByModule.getOrDefault(module.getId(), 0L);
            result.add(PrepModuleResponse.builder()
                    .id(module.getId())
                    .code(module.getCode())
                    .title(module.getTitle())
                    .description(module.getDescription())
                    .topicCount((int) total)
                    .completedTopics((int) completed)
                    .progressPercent(percent(completed, total))
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PrepModuleDetailResponse getModuleDetail(Long moduleId) {
        PrepModule module = moduleRepository.findByIdAndActiveTrue(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Preparation module", moduleId));

        List<PrepTopic> topics = topicRepository.findByModuleIdAndActiveTrueOrderBySortOrderAsc(moduleId);
        Map<Long, StudentPrepProgress> progressByTopic = progressByTopic(currentStudentProfile().orElse(null), topics);

        List<PrepTopicResponse> topicResponses = new ArrayList<>(topics.size());
        for (PrepTopic topic : topics) {
            topicResponses.add(toTopicResponse(topic, progressByTopic.get(topic.getId())));
        }

        return PrepModuleDetailResponse.builder()
                .id(module.getId())
                .code(module.getCode())
                .title(module.getTitle())
                .description(module.getDescription())
                .sortOrder(module.getSortOrder())
                .topics(topicResponses)
                .build();
    }

    @Transactional(readOnly = true)
    public PrepTopicDetailResponse getTopicDetail(Long topicId) {
        PrepTopic topic = activeTopic(topicId);
        StudentPrepProgress progress = progressByTopic(currentStudentProfile().orElse(null), List.of(topic))
                .get(topic.getId());
        List<PrepQuestionResponse> questions = questionRepository
                .findByTopicIdAndActiveTrueOrderBySortOrderAsc(topicId).stream()
                .map(this::toQuestionResponse)
                .toList();

        return PrepTopicDetailResponse.builder()
                .id(topic.getId())
                .code(topic.getCode())
                .title(topic.getTitle())
                .description(topic.getDescription())
                .studyGuide(topic.getStudyGuide())
                .estimatedMinutes(topic.getEstimatedMinutes())
                .sortOrder(topic.getSortOrder())
                .completed(progress != null ? progress.getCompleted() : null)
                .confidence(progress != null && progress.getConfidence() != null ? progress.getConfidence().name() : null)
                .moduleId(topic.getModule().getId())
                .moduleCode(topic.getModule().getCode())
                .moduleTitle(topic.getModule().getTitle())
                .questions(questions)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PrepQuestionResponse> getQuestions(Long topicId) {
        activeTopic(topicId);
        return questionRepository.findByTopicIdAndActiveTrueOrderBySortOrderAsc(topicId).stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrepProgressResponse> getMyProgress() {
        StudentProfile profile = requireStudentWithProfile();
        return progressRepository.findByStudentProfileId(profile.getId()).stream()
                .filter(p -> Boolean.TRUE.equals(p.getTopic().getActive())
                        && Boolean.TRUE.equals(p.getTopic().getModule().getActive()))
                .map(this::toProgressResponse)
                .toList();
    }

    @Transactional
    public PrepProgressResponse updateMyProgress(Long topicId, UpdatePrepProgressRequest request) {
        StudentProfile profile = requireStudentWithProfile();

        if (request == null
                || (request.getCompleted() == null && isBlank(request.getConfidence()))) {
            throw new BadRequestException("Provide at least one of completed or confidence.");
        }
        PrepConfidence confidence = parseConfidence(request.getConfidence());

        PrepTopic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Preparation topic", topicId));
        if (!Boolean.TRUE.equals(topic.getActive())
                || !Boolean.TRUE.equals(topic.getModule().getActive())) {
            throw new BadRequestException("Progress can only be recorded on active topics.");
        }

        StudentPrepProgress progress = progressRepository
                .findByStudentProfileIdAndTopicId(profile.getId(), topicId)
                .orElseGet(() -> StudentPrepProgress.builder()
                        .studentProfile(profile)
                        .topic(topic)
                        .completed(false)
                        .build());

        if (request.getCompleted() != null) {
            progress.setCompleted(request.getCompleted());
        }
        progress.setConfidence(confidence);

        progress = progressRepository.save(progress);
        return toProgressResponse(progress);
    }

    @Transactional(readOnly = true)
    public PrepSummaryResponse getMySummary() {
        StudentProfile profile = requireStudentWithProfile();

        Map<Long, Long> topicCounts = topicRepository.countActiveTopicsGroupedByModule().stream()
                .collect(Collectors.toMap(PrepTopicRepository.TopicCountProjection::getModuleId,
                        PrepTopicRepository.TopicCountProjection::getCnt));

        long completedActive = 0;
        long low = 0;
        long medium = 0;
        long high = 0;
        Map<Long, Long> completedByModule = new HashMap<>();
        for (StudentPrepProgress p : progressRepository.findByStudentProfileId(profile.getId())) {
            PrepTopic topic = p.getTopic();
            if (!Boolean.TRUE.equals(topic.getActive())
                    || !Boolean.TRUE.equals(topic.getModule().getActive())) {
                continue;
            }
            if (Boolean.TRUE.equals(p.getCompleted())) {
                completedActive++;
                completedByModule.merge(topic.getModule().getId(), 1L, Long::sum);
            }
            if (p.getConfidence() != null) {
                switch (p.getConfidence()) {
                    case LOW -> low++;
                    case MEDIUM -> medium++;
                    case HIGH -> high++;
                }
            }
        }

        long totalTopics = topicCounts.values().stream().mapToLong(Long::longValue).sum();

        List<PrepModuleProgressResponse> moduleProgress = new ArrayList<>();
        for (PrepModule module : moduleRepository.findByActiveTrueOrderBySortOrderAsc()) {
            long total = topicCounts.getOrDefault(module.getId(), 0L);
            long completed = completedByModule.getOrDefault(module.getId(), 0L);
            moduleProgress.add(PrepModuleProgressResponse.builder()
                    .moduleId(module.getId())
                    .moduleCode(module.getCode())
                    .moduleTitle(module.getTitle())
                    .totalTopics((int) total)
                    .completedTopics((int) completed)
                    .progressPercent(percent(completed, total))
                    .build());
        }

        return PrepSummaryResponse.builder()
                .totalTopics((int) totalTopics)
                .completedTopics((int) completedActive)
                .completionPercent(percent(completedActive, totalTopics))
                .lowConfidenceTopics((int) low)
                .mediumConfidenceTopics((int) medium)
                .highConfidenceTopics((int) high)
                .moduleProgress(moduleProgress)
                .build();
    }

    @Transactional(readOnly = true)
    public PrepSearchResponse search(String rawQuery) {
        PrepSearchResponse response = new PrepSearchResponse();
        response.setModules(new ArrayList<>());
        response.setTopics(new ArrayList<>());
        response.setQuestions(new ArrayList<>());
        if (isBlank(rawQuery)) {
            return response;
        }
        String q = rawQuery.trim();

        for (PrepModule m : moduleRepository.searchActive(q, PageRequest.of(0, 8))) {
            response.getModules().add(PrepSearchResponse.PrepSearchHit.builder()
                    .id(m.getId()).title(m.getTitle()).context(m.getCode()).build());
        }
        for (PrepTopic t : topicRepository.searchActive(q, PageRequest.of(0, 8))) {
            response.getTopics().add(PrepSearchResponse.PrepSearchHit.builder()
                    .id(t.getId()).title(t.getTitle()).context(t.getModule().getTitle()).build());
        }
        for (PrepQuestion question : questionRepository.searchActive(q, PageRequest.of(0, 8))) {
            response.getQuestions().add(PrepSearchResponse.PrepSearchHit.builder()
                    .id(question.getId())
                    .title(question.getQuestion())
                    .context(question.getTopic().getTitle()).build());
        }
        return response;
    }

    private Optional<StudentProfile> currentStudentProfile() {
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() != Role.STUDENT && currentUser.getRole() != Role.PR) {
            return Optional.empty();
        }
        return profileRepository.findByUserId(currentUser.getId());
    }

    private StudentProfile requireStudentWithProfile() {
        return currentStudentProfile()
                .orElseThrow(() -> new ForbiddenException(
                        "Preparation progress is available only to students and PRs linked to a student profile."));
    }

    private PrepTopic activeTopic(Long topicId) {
        return topicRepository.findByIdAndActiveTrue(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Preparation topic", topicId));
    }

    private Map<Long, StudentPrepProgress> progressByTopic(StudentProfile profile, List<PrepTopic> topics) {
        if (profile == null || topics.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> topicIds = topics.stream().map(PrepTopic::getId).toList();
        return progressRepository.findByStudentProfileIdAndTopicIdIn(profile.getId(), topicIds).stream()
                .collect(Collectors.toMap(p -> p.getTopic().getId(), p -> p));
    }

    private PrepConfidence parseConfidence(String confidence) {
        if (isBlank(confidence)) {
            return null;
        }
        try {
            return PrepConfidence.valueOf(confidence.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid confidence. Use LOW, MEDIUM, HIGH or null.");
        }
    }

    private PrepTopicResponse toTopicResponse(PrepTopic topic, StudentPrepProgress progress) {
        return PrepTopicResponse.builder()
                .id(topic.getId())
                .code(topic.getCode())
                .title(topic.getTitle())
                .description(topic.getDescription())
                .studyGuide(topic.getStudyGuide())
                .estimatedMinutes(topic.getEstimatedMinutes())
                .sortOrder(topic.getSortOrder())
                .completed(progress != null ? progress.getCompleted() : null)
                .confidence(progress != null && progress.getConfidence() != null ? progress.getConfidence().name() : null)
                .build();
    }

    private PrepQuestionResponse toQuestionResponse(PrepQuestion question) {
        return PrepQuestionResponse.builder()
                .id(question.getId())
                .question(question.getQuestion())
                .answerGuide(question.getAnswerGuide())
                .difficulty(question.getDifficulty().name())
                .sortOrder(question.getSortOrder())
                .build();
    }

    private PrepProgressResponse toProgressResponse(StudentPrepProgress progress) {
        PrepTopic topic = progress.getTopic();
        PrepModule module = topic.getModule();
        return PrepProgressResponse.builder()
                .topicId(topic.getId())
                .topicCode(topic.getCode())
                .topicTitle(topic.getTitle())
                .moduleId(module.getId())
                .moduleCode(module.getCode())
                .moduleTitle(module.getTitle())
                .completed(progress.getCompleted())
                .confidence(progress.getConfidence() != null ? progress.getConfidence().name() : null)
                .updatedAt(progress.getUpdatedAt() != null ? progress.getUpdatedAt().toString() : null)
                .build();
    }

    private double percent(long completed, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(completed * 1000.0 / total) / 10.0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}