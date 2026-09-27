package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoEmailWebhookEvent;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailWebhookEventRepository extends MongoRepository<MongoEmailWebhookEvent, String> {

    Optional<MongoEmailWebhookEvent> findById(String id);
}
