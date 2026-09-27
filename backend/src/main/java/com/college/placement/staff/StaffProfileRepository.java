package com.college.placement.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    /**
     * The single lookup needed by the profile read path. The unique constraint on
     * {@code user_id} carries the index, so no extra index is warranted for a
     * table of this size.
     */
    Optional<StaffProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
