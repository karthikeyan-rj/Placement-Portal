package com.college.placement.placement;

import com.college.placement.common.enums.PlacementDriveStatus;
import com.college.placement.company.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "placement_drives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlacementDrive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "job_role", nullable = false, length = 255, columnDefinition = "VARCHAR(255)")
    private String jobRole;

    @Column(name = "package_lpa")
    private BigDecimal packageLpa;

    @Column(name = "drive_date")
    private LocalDate driveDate;

    @Column(name = "registration_deadline")
    private LocalDate registrationDeadline;

    @Column(length = 255, columnDefinition = "VARCHAR(255)")
    private String location;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PlacementDriveStatus status = PlacementDriveStatus.UPCOMING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
