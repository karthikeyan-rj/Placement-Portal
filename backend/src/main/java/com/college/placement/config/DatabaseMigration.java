package com.college.placement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@Order(0)
public class DatabaseMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Running database migration to fix bytea columns...");
        try {
            List<String> migrations = List.of(
                // Users table
                "ALTER TABLE users ALTER COLUMN name TYPE VARCHAR(255) USING name::text",
                "ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(255) USING email::text",
                "ALTER TABLE users ALTER COLUMN password_hash TYPE VARCHAR(255) USING password_hash::text",

                // Departments table
                "ALTER TABLE departments ALTER COLUMN name TYPE VARCHAR(255) USING name::text",

                // Companies table
                "ALTER TABLE companies ALTER COLUMN name TYPE VARCHAR(255) USING name::text",
                "ALTER TABLE companies ALTER COLUMN website TYPE VARCHAR(500) USING website::text",

                // Contact requests table
                "ALTER TABLE contact_requests ALTER COLUMN subject TYPE VARCHAR(255) USING subject::text",

                // Messages table
                "ALTER TABLE messages ALTER COLUMN title TYPE VARCHAR(255) USING title::text",

                // Audit logs table
                "ALTER TABLE audit_logs ALTER COLUMN action TYPE VARCHAR(255) USING action::text",
                "ALTER TABLE audit_logs ALTER COLUMN entity_type TYPE VARCHAR(255) USING entity_type::text",

                // Student profiles table
                "ALTER TABLE student_profiles ALTER COLUMN register_number TYPE VARCHAR(255) USING register_number::text",
                "ALTER TABLE student_profiles ALTER COLUMN phone TYPE VARCHAR(20) USING phone::text",
                "ALTER TABLE student_profiles ALTER COLUMN batch TYPE VARCHAR(50) USING batch::text",
                "ALTER TABLE student_profiles ALTER COLUMN section TYPE VARCHAR(20) USING section::text",

                // Student professionals table
                "ALTER TABLE student_professionals ALTER COLUMN resume_url TYPE VARCHAR(500) USING resume_url::text",
                "ALTER TABLE student_professionals ALTER COLUMN github_url TYPE VARCHAR(500) USING github_url::text",
                "ALTER TABLE student_professionals ALTER COLUMN linkedin_url TYPE VARCHAR(500) USING linkedin_url::text",
                "ALTER TABLE student_professionals ALTER COLUMN portfolio_url TYPE VARCHAR(500) USING portfolio_url::text",

                // Placement drives table
                "ALTER TABLE placement_drives ALTER COLUMN job_role TYPE VARCHAR(255) USING job_role::text",
                "ALTER TABLE placement_drives ALTER COLUMN location TYPE VARCHAR(255) USING location::text",

                // Placement records table
                "ALTER TABLE placement_records ALTER COLUMN status TYPE VARCHAR(50) USING status::text",

                // Student interviews table
                "ALTER TABLE student_interviews ALTER COLUMN round_name TYPE VARCHAR(255) USING round_name::text",

                // Placement preference history table
                "ALTER TABLE placement_preference_history ALTER COLUMN reason TYPE VARCHAR(500) USING reason::text"
            );

            int successCount = 0;
            for (String sql : migrations) {
                try {
                    jdbcTemplate.execute(sql);
                    successCount++;
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("does not exist") || msg.contains("cannot be cast"))) {
                        // Column doesn't exist or already correct type, skip
                        log.debug("Skipping migration (not needed): {}", sql);
                    } else {
                        log.warn("Migration failed: {} - {}", sql, msg);
                    }
                }
            }

            log.info("Database migration completed. {}/{} migrations applied.", successCount, migrations.size());
        } catch (Exception e) {
            log.error("Database migration failed: {}", e.getMessage());
        }
    }
}
