package com.college.placement.messaging.mongo.repository;

import com.college.placement.messaging.mongo.document.MongoDriveEmailOutbox;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DriveEmailOutboxRepository extends MongoRepository<MongoDriveEmailOutbox, String> {

    List<MongoDriveEmailOutbox> findByDriveIdAndEventKey(Long driveId, String eventKey);
}
