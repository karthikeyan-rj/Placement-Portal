package com.college.placement.student;

import com.college.placement.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_access_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAccessCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id")
    private StudentProfile studentProfile;

    @Column(name = "register_number", nullable = false, length = 255, columnDefinition = "VARCHAR(255)")
    private String registerNumber;

    @Column(length = 255, columnDefinition = "VARCHAR(255)")
    private String name;

    @Column(name = "department_code", length = 255, columnDefinition = "VARCHAR(255)")
    private String departmentCode;

    @Column(length = 255, columnDefinition = "VARCHAR(255)")
    private String email;

    @Column(name = "code_hash", nullable = false, length = 255, columnDefinition = "VARCHAR(255)")
    private String codeHash;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
