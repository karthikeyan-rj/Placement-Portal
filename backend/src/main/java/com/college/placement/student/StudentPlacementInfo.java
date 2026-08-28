package com.college.placement.student;

import com.college.placement.common.enums.PlacementStatus;
import com.college.placement.company.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_placement_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentPlacementInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false, unique = true)
    private StudentProfile studentProfile;

    @Column(name = "placement_interested")
    @Builder.Default
    private Boolean placementInterested = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement_status", nullable = false)
    @Builder.Default
    private PlacementStatus placementStatus = PlacementStatus.NOT_PLACED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placed_company_id")
    private Company placedCompany;

    @Column(name = "package_lpa")
    private BigDecimal packageLpa;

    @Column(name = "interviews_attended")
    @Builder.Default
    private Integer interviewsAttended = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
