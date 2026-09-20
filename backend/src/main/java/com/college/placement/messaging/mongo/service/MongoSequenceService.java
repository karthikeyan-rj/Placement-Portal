package com.college.placement.messaging.mongo.service;

import com.college.placement.messaging.mongo.document.MongoSequence;

import lombok.RequiredArgsConstructor;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MongoSequenceService {

    private final MongoTemplate mongoTemplate;

    public long next(String key) {
        MongoSequence result = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(key)),
                new Update().inc("sequence", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                MongoSequence.class);
        return result == null ? 1 : result.getSequence();
    }

    public void seedIfAbsent(String key, long value) {
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(key)),
                new Update().setOnInsert("sequence", value),
                MongoSequence.class);
    }
}