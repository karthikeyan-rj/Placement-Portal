package com.college.placement.placement;

import com.college.placement.department.Department;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "eligibility_criteria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibilityCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_drive_id", nullable = false, unique = true)
    private PlacementDrive placementDrive;

    @Column(name = "min_cgpa")
    private BigDecimal minCgpa;

    @Column(name = "max_active_backlogs")
    private Integer maxActiveBacklogs;

    @Column(name = "min_tenth_pct")
    private BigDecimal minTenthPct;

    @Column(name = "min_twelfth_pct")
    private BigDecimal minTwelfthPct;

    @Column(name = "min_diploma_pct")
    private BigDecimal minDiplomaPct;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "eligibility_allowed_departments",
        joinColumns = @JoinColumn(name = "eligibility_criteria_id"),
        inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @Builder.Default
    private Set<Department> allowedDepartments = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
