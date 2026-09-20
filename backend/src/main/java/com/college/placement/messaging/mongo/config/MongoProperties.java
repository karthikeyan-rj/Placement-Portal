package com.college.placement.messaging.mongo.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.mongodb")
public class MongoProperties {

    private boolean enabled = true;
    private boolean required = false;
    private boolean migrateEnabled = false;
    private boolean validateEnabled = false;
    private boolean perfEnabled = false;
}