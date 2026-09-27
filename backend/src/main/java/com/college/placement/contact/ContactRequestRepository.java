package com.college.placement.contact;

import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {

    Page<ContactRequest> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId, Pageable pageable);

    Page<ContactRequest> findByStudentProfileUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ContactRequest> findByTargetUserIdAndStatusOrderByCreatedAtDesc(Long targetUserId, String status, Pageable pageable);

    long countByTargetUserIdAndStatus(Long targetUserId, String status);

    long countByStudentProfileIdAndStatus(Long studentProfileId, String status);

    /**
     * Incoming list for a coordinator. The requester profile, both user records
     * and both departments are fetched up front so rendering a page of requests
     * does not degrade into one extra query per row.
     */
    @Query(value = "select cr from ContactRequest cr "
            + "join fetch cr.studentProfile sp "
            + "join fetch sp.user u "
            + "left join fetch u.department "
            + "join fetch cr.targetUser t "
            + "left join fetch t.department "
            + "where t.id = :targetUserId",
            countQuery = "select count(cr) from ContactRequest cr where cr.targetUser.id = :targetUserId")
    Page<ContactRequest> findIncomingForUser(@Param("targetUserId") Long targetUserId, Pageable pageable);

    /**
     * Sent list for the requester, fetched with the same graph as
     * {@link #findIncomingForUser(Long, Pageable)} so the response DTO is
     * assembled from a single query.
     */
    @Query(value = "select cr from ContactRequest cr "
            + "join fetch cr.studentProfile sp "
            + "join fetch sp.user u "
            + "left join fetch u.department "
            + "join fetch cr.targetUser t "
            + "left join fetch t.department "
            + "where sp.user.id = :requesterUserId",
            countQuery = "select count(cr) from ContactRequest cr where cr.studentProfile.user.id = :requesterUserId")
    Page<ContactRequest> findSentByUser(@Param("requesterUserId") Long requesterUserId, Pageable pageable);

    /**
     * True when the requester already has a request in the given state against
     * the target. Used to reject a second PENDING request for the same pair.
     */
    @Query("select (count(cr) > 0) from ContactRequest cr "
            + "where cr.studentProfile.user.id = :requesterUserId "
            + "and cr.targetUser.id = :targetUserId "
            + "and cr.status = :status")
    boolean existsBetween(@Param("requesterUserId") Long requesterUserId,
                          @Param("targetUserId") Long targetUserId,
                          @Param("status") ContactRequestStatus status);

    /**
     * The single source of truth for the contact-request direct-messaging
     * exception. Every invariant is enforced inside this single cheap
     * count-projection, so a malformed, legacy or hand-inserted row cannot grant
     * access that the create path would never have allowed:
     *
     * <ul>
     *   <li>status must be ACCEPTED or RESOLVED (supplied by the caller);</li>
     *   <li>the stored requester role must be STUDENT or PR;</li>
     *   <li>the stored target role must be PC - which also makes PO structurally
     *       unable to be either side, because PO satisfies neither predicate;</li>
       *   <li>requester and target must still share a department. This re-checks
       *       the authoritative current departments rather than trusting
       *       create-time validation, so a row inserted after the two users were
       *       moved apart stops granting immediately;</li>
       *   <li>both users must still be active, which is the create-time active
       *       target rule re-checked against current state on both sides. A row
       *       whose PC has since been deactivated or deactivated-by-admin stops
       *       granting immediately;</li>
       *   <li>the pair must match exactly, in either order, so one request is
       *       enough for both participants and no third party can borrow it.</li>
       * </ul>
       *
       * A user with no department on either side cannot satisfy the department
       * equality, and a null or false active flag fails the active predicate, so
       * such rows never grant.
       */
      @Query("select (count(cr) > 0) from ContactRequest cr "
              + "where cr.status in :statuses "
              + "and cr.studentProfile.user.role in :requesterRoles "
              + "and cr.targetUser.role = :targetRole "
              + "and cr.studentProfile.user.active = true "
              + "and cr.targetUser.active = true "
              + "and cr.studentProfile.user.department.id = cr.targetUser.department.id "
              + "and ((cr.studentProfile.user.id = :userA and cr.targetUser.id = :userB) "
              + "or (cr.studentProfile.user.id = :userB and cr.targetUser.id = :userA))")
    boolean existsMessagingGrantBetween(@Param("userA") Long userA,
                                        @Param("userB") Long userB,
                                        @Param("statuses") Collection<ContactRequestStatus> statuses,
                                        @Param("requesterRoles") Collection<Role> requesterRoles,
                                        @Param("targetRole") Role targetRole);

    @Query("select cr.status, count(cr) from ContactRequest cr "
            + "where cr.targetUser.id = :targetUserId group by cr.status")
    List<Object[]> countIncomingGroupedByStatus(@Param("targetUserId") Long targetUserId);

    @Query("select cr.status, count(cr) from ContactRequest cr "
            + "where cr.studentProfile.user.id = :requesterUserId group by cr.status")
    List<Object[]> countSentGroupedByStatus(@Param("requesterUserId") Long requesterUserId);
}
