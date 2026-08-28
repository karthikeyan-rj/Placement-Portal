package com.college.placement.student;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_academics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAcademic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false, unique = true)
    private StudentProfile studentProfile;

    @Column(name = "tenth_percentage")
    private BigDecimal tenthPercentage;

    @Column(name = "twelfth_percentage")
    private BigDecimal twelfthPercentage;

    @Column(name = "diploma_percentage")
    private BigDecimal diplomaPercentage;

    private BigDecimal cgpa;

    @Column(name = "active_backlogs")
    @Builder.Default
    private Integer activeBacklogs = 0;

    @Column(name = "history_of_backlogs")
    @Builder.Default
    private Integer historyOfBacklogs = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
