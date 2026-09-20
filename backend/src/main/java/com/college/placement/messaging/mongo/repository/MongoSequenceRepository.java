package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoSequence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoSequenceRepository extends MongoRepository<MongoSequence, String> {
}