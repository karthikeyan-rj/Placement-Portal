package com.college.placement.messaging.mongo.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    private Storage storage = Storage.POSTGRES;

    public enum Storage {
        POSTGRES,
        MONGO
    }
}