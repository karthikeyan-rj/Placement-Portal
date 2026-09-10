package com.college.placement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Wires the dev-only bulk demo data entry points.
 *
 * <ul>
 *   <li>{@code SEED_BULK_DEMO_DATA=true} → seeds bulk demo data (default off)</li>
 *   <li>{@code SEED_BULK_DEMO_CLEANUP=true} → removes only the bulk namespace (default off)</li>
 * </ul>
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class BulkSeedDataInitializer {

    private final BulkSeedService bulkSeedService;

    @Bean
    @Order(2)
    @ConditionalOnProperty(name = "app.seed-bulk-demo-cleanup", havingValue = "true")
    CommandLineRunner bulkCleanupRunner() {
        return args -> bulkSeedService.cleanup();
    }

    @Bean
    @Order(3)
    @ConditionalOnProperty(name = "app.seed-bulk-demo-data", havingValue = "true")
    CommandLineRunner bulkSeedRunner() {
        return args -> bulkSeedService.seed();
    }
}