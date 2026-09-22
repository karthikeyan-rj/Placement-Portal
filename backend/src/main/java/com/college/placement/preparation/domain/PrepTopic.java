package com.college.placement.preparation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "prep_topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrepTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private PrepModule module;

    @Column(nullable = false, unique = true, length = 100, columnDefinition = "VARCHAR(100)")
    private String code;

    @Column(nullable = false, length = 255, columnDefinition = "VARCHAR(255)")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "study_guide", columnDefinition = "TEXT", nullable = false)
    private String studyGuide;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

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