package com.college.placement.registration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationVerificationCodeRepository extends JpaRepository<RegistrationVerificationCode, Long> {

    /**
     * Finds the latest unexpired, unused verification code for the given authorized student.
     */
    @Query("""
        SELECT r FROM RegistrationVerificationCode r
        WHERE r.authorizedStudent.id = :authorizedStudentId
          AND r.usedAt IS NULL
          AND r.expiresAt > :now
        ORDER BY r.createdAt DESC
        """)
    List<RegistrationVerificationCode> findActiveByAuthorizedStudentId(
            @Param("authorizedStudentId") Long authorizedStudentId,
            @Param("now") LocalDateTime now);

    /**
     * Counts how many codes have been sent for this email in the given time window (rate limiting).
     */
    @Query("SELECT COUNT(r) FROM RegistrationVerificationCode r WHERE r.email = :email AND r.createdAt > :since")
    long countByEmailSince(@Param("email") String email, @Param("since") LocalDateTime since);

    /**
     * Invalidates all outstanding (unused, unexpired) codes for the given authorized student.
     */
    @Modifying
    @Query("""
        UPDATE RegistrationVerificationCode r
        SET r.usedAt = :now
        WHERE r.authorizedStudent.id = :authorizedStudentId
          AND r.usedAt IS NULL
        """)
    int invalidateAllForAuthorizedStudent(
            @Param("authorizedStudentId") Long authorizedStudentId,
            @Param("now") LocalDateTime now);
}
