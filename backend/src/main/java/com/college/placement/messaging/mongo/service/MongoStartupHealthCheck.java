package com.college.placement.messaging.mongo.service;

import com.college.placement.messaging.mongo.config.MongoProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mongodb.enabled", havingValue = "true", matchIfMissing = false)
public class MongoStartupHealthCheck implements CommandLineRunner {

    private static final List<String> REQUIRED_COLLECTIONS = List.of(
            "messages", "message_recipients", "clarification_threads", "clarification_entries");

    private static final List<String> ON_DEMAND_COLLECTIONS = List.of("mongo_sequences");

    private final MongoTemplate mongoTemplate;
    private final MongoProperties mongoProperties;

    @Override
    public void run(String... args) {
        try {
            Document ping = mongoTemplate.executeCommand(new Document("ping", 1));
            String database = mongoTemplate.getDb().getName();
            Set<String> collections = mongoTemplate.getCollectionNames();
            log.info("[MONGO] HEALTH ping={} database={}", ping.get("ok"), database);
            boolean allPresent = true;
            for (String collection : REQUIRED_COLLECTIONS) {
                boolean present = collections.contains(collection);
                allPresent = allPresent && present;
                log.info("[MONGO] COLLECTION {} -> {}", collection, present ? "PASS" : "FAIL");
            }
            for (String collection : ON_DEMAND_COLLECTIONS) {
                boolean present = collections.contains(collection);
                log.info("[MONGO] COLLECTION {} -> {}", collection, present ? "PASS" : "ON_DEMAND");
            }
            if (mongoProperties.isRequired() && !allPresent) {
                throw new IllegalStateException("MongoDB is required but not all collections are present");
            }
        } catch (Exception e) {
            log.warn("[MONGO] HEALTH FAIL connected=false error={}", e.getClass().getSimpleName());
            if (mongoProperties.isRequired()) {
                throw new IllegalStateException("MongoDB is required but unreachable", e);
            }
        }
    }
}