package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoMessageEmailOutbox;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageEmailOutboxRepository extends MongoRepository<MongoMessageEmailOutbox, String> {

    List<MongoMessageEmailOutbox> findByMessageId(Long messageId);
}