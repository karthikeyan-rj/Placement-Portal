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
        log.info("Running database migration step (column fixes + performance indexes)...");
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
                "ALTER TABLE placement_preference_history ALTER COLUMN reason TYPE VARCHAR(500) USING reason::text",

                // Student access codes - allow pre-registration keying by register number
                // (student_profile_id is attached only after a profile is created)
                "ALTER TABLE student_access_codes ALTER COLUMN student_profile_id DROP NOT NULL",
                "ALTER TABLE student_access_codes ADD COLUMN IF NOT EXISTS register_number VARCHAR(255)",
                "ALTER TABLE student_access_codes ADD COLUMN IF NOT EXISTS name VARCHAR(255)",
                "ALTER TABLE student_access_codes ADD COLUMN IF NOT EXISTS department_code VARCHAR(255)",
                "ALTER TABLE student_access_codes ADD COLUMN IF NOT EXISTS email VARCHAR(255)",
                "ALTER TABLE student_access_codes ALTER COLUMN register_number SET NOT NULL",
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_sac_register_number ON student_access_codes (register_number)",

                // Performance indexes (evidence-based, see Phase 4G-C report):
                // place on FK columns and frequently filtered/sorted columns that previously
                // relied on sequential scans (verified via pg_indexes that only PK/unique
                // indexes existed on these tables at the time of the audit). Column names
                // match the @JoinColumn mappings in the JPA entities.
                "CREATE INDEX IF NOT EXISTS idx_messages_sender_created ON messages (sender_id, created_at DESC)",
                "CREATE INDEX IF NOT EXISTS idx_message_recipients_recipient_created ON message_recipients (recipient_id, created_at DESC)",
                "CREATE INDEX IF NOT EXISTS idx_message_recipients_message ON message_recipients (message_id)",
                "CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs (created_at DESC)",
                "CREATE INDEX IF NOT EXISTS idx_audit_logs_performed_by_created ON audit_logs (performed_by, created_at DESC)",
                "CREATE INDEX IF NOT EXISTS idx_placement_drives_status_date ON placement_drives (status, drive_date DESC)",
                "CREATE INDEX IF NOT EXISTS idx_placement_drives_company ON placement_drives (company_id)",
                "CREATE INDEX IF NOT EXISTS idx_contact_requests_target_created ON contact_requests (target_user_id, created_at DESC)",

                // ---- Phase 7A.1: Interview Preparation Hub tables (backend-managed prep content) ----
                "CREATE TABLE IF NOT EXISTS prep_modules (" +
                        "id BIGSERIAL PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, title VARCHAR(255) NOT NULL, " +
                        "description TEXT, sort_order INTEGER NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE, " +
                        "created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE TABLE IF NOT EXISTS prep_topics (" +
                        "id BIGSERIAL PRIMARY KEY, module_id BIGINT NOT NULL REFERENCES prep_modules(id), " +
                        "code VARCHAR(100) NOT NULL UNIQUE, title VARCHAR(255) NOT NULL, description TEXT, " +
                        "study_guide TEXT NOT NULL, estimated_minutes INTEGER, sort_order INTEGER NOT NULL DEFAULT 0, " +
                        "active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_prep_topics_module_sort ON prep_topics (module_id, sort_order)",
                "CREATE TABLE IF NOT EXISTS prep_questions (" +
                        "id BIGSERIAL PRIMARY KEY, topic_id BIGINT NOT NULL REFERENCES prep_topics(id), " +
                        "question TEXT NOT NULL, answer_guide TEXT NOT NULL, difficulty VARCHAR(10) NOT NULL DEFAULT 'MEDIUM', " +
                        "sort_order INTEGER NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE, " +
                        "created_at TIMESTAMP, updated_at TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_prep_questions_topic_sort ON prep_questions (topic_id, sort_order)",
                "CREATE TABLE IF NOT EXISTS student_prep_progress (" +
                        "id BIGSERIAL PRIMARY KEY, student_profile_id BIGINT NOT NULL REFERENCES student_profiles(id), " +
                        "topic_id BIGINT NOT NULL REFERENCES prep_topics(id), completed BOOLEAN NOT NULL DEFAULT FALSE, " +
                        "confidence VARCHAR(10), updated_at TIMESTAMP, " +
                        "CONSTRAINT uq_student_prep_topic UNIQUE (student_profile_id, topic_id))"
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
