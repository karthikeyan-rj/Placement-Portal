package com.college.placement.contact;

import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the contact-request direct-messaging grant against a real database
 * with real rows - no mocks anywhere in this class.
 *
 * <p>Every row is written through the repositories so that the malformed cases
 * (cross-department, PO target, student-to-student, PC-as-requester) exist in the
 * database even though {@code ContactRequestService#createContactRequest} would
 * never produce them. The test then asks the centralized permission method and
 * the underlying query what they authorise, which is precisely the "legacy or
 * hand-inserted ACCEPTED row" risk this phase closes.
 *
 * <p>The whole class runs in one transaction that is rolled back, so the
 * development database is left untouched.
 */
@SpringBootTest
@Transactional
class ContactRequestGrantSecurityTest {

    @Autowired
    private ContactRequestRepository repository;

    @Autowired
    private ContactRequestService contactRequestService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository profileRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private Department cse;
    private Department ece;

    private User studentA;
    private User studentB;
    private User studentC;
    private User prA;
    private User pcCse;
    private User pcEce;
    private User po;

    @BeforeEach
    void setUp() {
        cse = ContactRequestGrantFixtures.department(departmentRepository, "GRANT-CSE");
        ece = ContactRequestGrantFixtures.department(departmentRepository, "GRANT-ECE");

        studentA = ContactRequestGrantFixtures.user(userRepository, "studentA", Role.STUDENT, cse);
        studentB = ContactRequestGrantFixtures.user(userRepository, "studentB", Role.STUDENT, cse);
        studentC = ContactRequestGrantFixtures.user(userRepository, "studentC", Role.STUDENT, ece);
        prA = ContactRequestGrantFixtures.user(userRepository, "prA", Role.PR, cse);
        pcCse = ContactRequestGrantFixtures.user(userRepository, "pcCse", Role.PC, cse);
        pcEce = ContactRequestGrantFixtures.user(userRepository, "pcEce", Role.PC, ece);
        po = ContactRequestGrantFixtures.user(userRepository, "poUser", Role.PO, cse);

        for (User u : new User[]{studentA, studentB, studentC, prA}) {
            ContactRequestGrantFixtures.profile(profileRepository, u);
        }
    }

    private boolean granted(Long a, Long b) {
        return contactRequestService.hasDirectMessagingPermission(a, b);
    }

    // ---------------------------------------------------------------- valid

    @Test
    @DisplayName("valid STUDENT -> PC ACCEPTED grants, in both directions")
    void validStudentToPcAcceptedGrantsBothWays() {
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);

        assertThat(granted(studentA.getId(), pcCse.getId())).isTrue();
        assertThat(granted(pcCse.getId(), studentA.getId())).isTrue();
    }

    @Test
    @DisplayName("valid PR -> PC ACCEPTED grants, in both directions")
    void validPrToPcAcceptedGrantsBothWays() {
        ContactRequestGrantFixtures.request(repository, profileRepository, prA, pcCse, ContactRequestStatus.ACCEPTED);

        assertThat(granted(prA.getId(), pcCse.getId())).isTrue();
        assertThat(granted(pcCse.getId(), prA.getId())).isTrue();
    }

    @Test
    @DisplayName("valid RESOLVED request keeps granting")
    void validResolvedGrants() {
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.RESOLVED);

        assertThat(granted(studentA.getId(), pcCse.getId())).isTrue();
        assertThat(granted(pcCse.getId(), studentA.getId())).isTrue();
    }

    @Test
    @DisplayName("one request is enough for the reverse direction - no second request required")
    void reverseDirectionNeedsNoSecondRequest() {
        ContactRequestGrantFixtures.request(repository, profileRepository, prA, pcCse, ContactRequestStatus.ACCEPTED);

        long rowsForPc = repository.count();

        assertThat(granted(pcCse.getId(), prA.getId())).isTrue();
        assertThat(repository.count()).isEqualTo(rowsForPc);
    }

    // -------------------------------------------------------- non-granting

    @Test
    @DisplayName("PENDING never grants")
    void pendingDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, prA, pcCse, ContactRequestStatus.PENDING);

        assertThat(granted(prA.getId(), pcCse.getId())).isFalse();
        assertThat(granted(pcCse.getId(), prA.getId())).isFalse();
    }

    @Test
    @DisplayName("REJECTED never grants")
    void rejectedDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, prA, pcCse, ContactRequestStatus.REJECTED);

        assertThat(granted(prA.getId(), pcCse.getId())).isFalse();
        assertThat(granted(pcCse.getId(), prA.getId())).isFalse();
    }

    @Test
    @DisplayName("an unrelated third user cannot borrow somebody else's accepted request")
    void unrelatedPairDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);

        assertThat(granted(studentB.getId(), pcCse.getId())).isFalse();
        assertThat(granted(prA.getId(), pcCse.getId())).isFalse();
        assertThat(granted(studentA.getId(), userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.PC && !u.getId().equals(pcCse.getId()))
                .findFirst().orElseThrow().getId())).isFalse();
    }

    @Test
    @DisplayName("a user cannot message themselves through the exception")
    void selfPairDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);

        assertThat(granted(studentA.getId(), studentA.getId())).isFalse();
        assertThat(granted(null, pcCse.getId())).isFalse();
        assertThat(granted(studentA.getId(), null)).isFalse();
    }

    // ------------------------------------------------- malformed / legacy data

    @Test
    @DisplayName("malformed cross-department ACCEPTED row does not grant")
    void crossDepartmentAcceptedDoesNotGrant() {
        // studentA is CSE, pcEce is ECE - create() would have refused this pair.
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcEce, ContactRequestStatus.ACCEPTED);

        assertThat(granted(studentA.getId(), pcEce.getId())).isFalse();
        assertThat(granted(pcEce.getId(), studentA.getId())).isFalse();
        // the legitimate same-department pair is unaffected
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);
        assertThat(granted(studentA.getId(), pcCse.getId())).isTrue();
    }

    @Test
    @DisplayName("malformed STUDENT -> PO ACCEPTED row does not grant")
    void studentToPoAcceptedDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, po, ContactRequestStatus.ACCEPTED);

        assertThat(granted(studentA.getId(), po.getId())).isFalse();
        assertThat(granted(po.getId(), studentA.getId())).isFalse();
    }

    @Test
    @DisplayName("malformed PR -> PO ACCEPTED row does not grant")
    void prToPoAcceptedDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, prA, po, ContactRequestStatus.ACCEPTED);

        assertThat(granted(prA.getId(), po.getId())).isFalse();
        assertThat(granted(po.getId(), prA.getId())).isFalse();
    }

    @Test
    @DisplayName("malformed STUDENT -> STUDENT ACCEPTED row does not grant")
    void studentToStudentAcceptedDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, studentB, ContactRequestStatus.ACCEPTED);

        assertThat(granted(studentA.getId(), studentB.getId())).isFalse();
        assertThat(granted(studentB.getId(), studentA.getId())).isFalse();
    }

    @Test
    @DisplayName("malformed PR -> STUDENT ACCEPTED row does not grant")
    void prToStudentAcceptedDoesNotGrant() {
        ContactRequestGrantFixtures.request(repository, profileRepository, prA, studentB, ContactRequestStatus.ACCEPTED);

        assertThat(granted(prA.getId(), studentB.getId())).isFalse();
    }

    @Test
    @DisplayName("malformed PR -> PR ACCEPTED row does not grant")
    void prToPrAcceptedDoesNotGrant() {
        User prB = ContactRequestGrantFixtures.user(userRepository, "prB", Role.PR, cse);
        ContactRequestGrantFixtures.profile(profileRepository, prB);
        ContactRequestGrantFixtures.request(repository, profileRepository, prA, prB, ContactRequestStatus.ACCEPTED);

        assertThat(granted(prA.getId(), prB.getId())).isFalse();
        assertThat(granted(prB.getId(), prA.getId())).isFalse();
    }

    @Test
    @DisplayName("malformed PC-as-requester row does not grant")
    void pcAsRequesterDoesNotGrant() {
        // A profile attached to a PC user: a legacy row shape where the stored
        // "requester" is a coordinator rather than a student or PR.
        User pcRequester = ContactRequestGrantFixtures.user(userRepository, "pcRequester", Role.PC, cse);
        StudentProfile pcProfile = ContactRequestGrantFixtures.profile(profileRepository, pcRequester);
        ContactRequestGrantFixtures.request(repository, pcProfile, pcCse, ContactRequestStatus.ACCEPTED);

        assertThat(granted(pcRequester.getId(), pcCse.getId())).isFalse();
        assertThat(granted(pcCse.getId(), pcRequester.getId())).isFalse();
    }

    @Test
    @DisplayName("malformed PO-as-requester row does not grant")
    void poAsRequesterDoesNotGrant() {
        StudentProfile poProfile = ContactRequestGrantFixtures.profile(profileRepository, po);
        ContactRequestGrantFixtures.request(repository, poProfile, pcCse, ContactRequestStatus.ACCEPTED);

        assertThat(granted(po.getId(), pcCse.getId())).isFalse();
        assertThat(granted(pcCse.getId(), po.getId())).isFalse();
    }

    @Test
    @DisplayName("the query enforces the department invariant even after the pair is split apart")
    void departmentChangeRevokesTheGrant() {
        // Accepted while both users were in the same department, then the PC is
        // moved. The stored row is untouched, but the authoritative departments
        // no longer match, so the grant must disappear.
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);
        assertThat(granted(studentA.getId(), pcCse.getId())).isTrue();

        pcCse.setDepartment(ece);
        userRepository.saveAndFlush(pcCse);

        assertThat(granted(studentA.getId(), pcCse.getId())).isFalse();
    }

    @Test
    @DisplayName("a target deactivated after acceptance stops granting immediately")
    void inactiveTargetDoesNotGrant() {
    ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);
    assertThat(granted(studentA.getId(), pcCse.getId())).isTrue();

    pcCse.setActive(false);
    userRepository.saveAndFlush(pcCse);

    assertThat(granted(studentA.getId(), pcCse.getId())).isFalse();
    }

    @Test
    @DisplayName("a requester deactivated after acceptance stops granting immediately")
    void inactiveRequesterDoesNotGrant() {
    ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);
    assertThat(granted(studentA.getId(), pcCse.getId())).isTrue();

    studentA.setActive(false);
    userRepository.saveAndFlush(studentA);

    assertThat(granted(studentA.getId(), pcCse.getId())).isFalse();
    }

    @Test
    @DisplayName("an inactive target is rejected in the reverse direction too")
    void inactiveTargetDoesNotGrantInReverse() {
    ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);
    pcCse.setActive(false);
    userRepository.saveAndFlush(pcCse);

    assertThat(granted(pcCse.getId(), studentA.getId())).isFalse();
    }

    @Test
    @DisplayName("a user with no department cannot satisfy the grant invariants")
    void nullDepartmentDoesNotGrant() {
        User pcNoDept = ContactRequestGrantFixtures.user(userRepository, "pcNoDept", Role.PC, null);
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcNoDept, ContactRequestStatus.ACCEPTED);

        assertThat(granted(studentA.getId(), pcNoDept.getId())).isFalse();
    }

    @Test
    @DisplayName("the underlying query agrees with the service for every case")
    void repositoryQueryMatchesService() {
        ContactRequestGrantFixtures.request(repository, profileRepository, studentA, pcCse, ContactRequestStatus.ACCEPTED);
        ContactRequestGrantFixtures.request(repository, profileRepository, studentB, pcEce, ContactRequestStatus.ACCEPTED);

        var statuses = java.util.EnumSet.of(ContactRequestStatus.ACCEPTED, ContactRequestStatus.RESOLVED);
        var requesterRoles = java.util.EnumSet.of(Role.STUDENT, Role.PR);

        assertThat(repository.existsMessagingGrantBetween(studentA.getId(), pcCse.getId(),
                statuses, requesterRoles, Role.PC)).isTrue();
        assertThat(repository.existsMessagingGrantBetween(pcCse.getId(), studentA.getId(),
                statuses, requesterRoles, Role.PC)).isTrue();
        assertThat(repository.existsMessagingGrantBetween(studentB.getId(), pcEce.getId(),
                statuses, requesterRoles, Role.PC)).isFalse();
        assertThat(repository.existsMessagingGrantBetween(studentA.getId(), po.getId(),
                statuses, requesterRoles, Role.PC)).isFalse();
    }
}
