package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoClarificationEntry;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MongoClarificationEntryRepository extends MongoRepository<MongoClarificationEntry, String> {

    List<MongoClarificationEntry> findByThreadIdOrderByCreatedAtAsc(Long threadId);

    Optional<MongoClarificationEntry> findByEntryId(Long entryId);

    List<MongoClarificationEntry> findByThreadId(Long threadId);
}