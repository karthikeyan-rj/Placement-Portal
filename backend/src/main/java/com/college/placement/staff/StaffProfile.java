package com.college.placement.staff;

import com.college.placement.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Editable professional profile for placement staff (PC and PO).
 *
 * <p>Ownership is fixed by role: {@code PC} and {@code PO} own a StaffProfile,
 * while {@code STUDENT} and {@code PR} own a StudentProfile. The role is never
 * stored here - it is read from {@link User} so there is a single source of
 * truth. Department is likewise read from the authoritative
 * {@code User.department} assignment, so a PC whose department changes shows
 * the new one immediately without any duplicate copy to migrate.
 *
 * <p>Rows are created lazily on the owner's first save, so a staff member who
 * has never filled anything in has no row and still gets a successful profile
 * read. Nothing here is security-sensitive: no password material, access code,
 * JWT or email-verification secret is stored.
 */
@Entity
@Table(name = "staff_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 25, columnDefinition = "VARCHAR(25)")
    private String phone;

    @Column(length = 120, columnDefinition = "VARCHAR(120)")
    private String designation;

    @Column(name = "office_location", length = 150, columnDefinition = "VARCHAR(150)")
    private String officeLocation;

    @Column(length = 1000, columnDefinition = "VARCHAR(1000)")
    private String bio;

    @Column(name = "linkedin_url", length = 500, columnDefinition = "VARCHAR(500)")
    private String linkedinUrl;

    /** User-entered areas of expertise, stored as a JSON array (not a delimited blob). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expertise", columnDefinition = "jsonb")
    private List<String> expertise;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
