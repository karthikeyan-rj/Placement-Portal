package com.college.placement.preparation.repository;

import com.college.placement.preparation.domain.StudentPrepProgress;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentPrepProgressRepository extends JpaRepository<StudentPrepProgress, Long> {

    @EntityGraph(attributePaths = {"topic", "topic.module"})
    List<StudentPrepProgress> findByStudentProfileId(Long studentProfileId);

    @EntityGraph(attributePaths = {"topic", "topic.module"})
    List<StudentPrepProgress> findByStudentProfileIdAndTopicIdIn(Long studentProfileId, Collection<Long> topicIds);

    @EntityGraph(attributePaths = {"topic", "topic.module"})
    Optional<StudentPrepProgress> findByStudentProfileIdAndTopicId(Long studentProfileId, Long topicId);
}