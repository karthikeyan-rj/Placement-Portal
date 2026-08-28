package com.college.placement.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {

    Page<ContactRequest> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId, Pageable pageable);

    Page<ContactRequest> findByStudentProfileUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ContactRequest> findByTargetUserIdAndStatusOrderByCreatedAtDesc(Long targetUserId, String status, Pageable pageable);

    long countByTargetUserIdAndStatus(Long targetUserId, String status);

    long countByStudentProfileIdAndStatus(Long studentProfileId, String status);
}
