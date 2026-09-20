package com.college.placement.messaging.mongo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MessagingProperties.class, MongoProperties.class})
public class MongoSupportConfig {
}