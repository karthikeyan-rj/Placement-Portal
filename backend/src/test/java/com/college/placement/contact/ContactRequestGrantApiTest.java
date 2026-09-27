package com.college.placement.contact;

import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the contact-request exception end to end through the real
 * {@code POST /api/messages} endpoint: real security filter chain, real
 * {@code MessageService}, real authorization, real database rows. Nothing is
 * mocked.
 *
 * <p>The legitimate path also goes through the real create/status endpoints so
 * the state machine is exercised, while the malformed rows are inserted straight
 * into the database because the API would correctly refuse to create them.
 *
 * <p>Runs in a single rolled-back transaction, so the development database is
 * left untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContactRequestGrantApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private User pr;
    private User student;
    private User pcCse;
    private User pcEce;
    private User po;

    @BeforeEach
    void setUp() {
        cse = ContactRequestGrantFixtures.department(departmentRepository, "APICSE");
        ece = ContactRequestGrantFixtures.department(departmentRepository, "APIECE");

        pr = ContactRequestGrantFixtures.user(userRepository, "apipr", Role.PR, cse);
        student = ContactRequestGrantFixtures.user(userRepository, "apistudent", Role.STUDENT, cse);
        pcCse = ContactRequestGrantFixtures.user(userRepository, "apipcCse", Role.PC, cse);
        pcEce = ContactRequestGrantFixtures.user(userRepository, "apipcEce", Role.PC, ece);
        po = ContactRequestGrantFixtures.user(userRepository, "apipo", Role.PO, cse);

        ContactRequestGrantFixtures.profile(profileRepository, pr);
        ContactRequestGrantFixtures.profile(profileRepository, student);
    }

    private String body(Map<String, Object> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> direct(Long recipientId) {
        return Map.of("title", "grant check", "content", "hello", "recipientIds", List.of(recipientId));
    }

    private void assertSend(String senderEmail, Long recipientId, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
                        .with(user(senderEmail))
                        .content(body(direct(recipientId))))
                .andExpect(status().is(expectedStatus));
    }

    // ----------------------------------------------------------------- unlock

    @Test
    @DisplayName("9: PR -> same-dept PC blocked before acceptance, allowed after ACCEPTED")
    void prToSameDeptPcBlockedThenAllowedAfterAcceptance() throws Exception {
        String prEmail = pr.getEmail();

        // no request exists yet
        assertSend(prEmail, pcCse.getId(), 400);

        // create the request through the real endpoint
        String created = mockMvc.perform(post("/api/contact-requests").contentType(MediaType.APPLICATION_JSON)
                        .with(user(prEmail))
                        .content(body(Map.of("targetUserId", pcCse.getId(), "subject", "s", "message", "m"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(created).path("data").path("id").asLong();

        // still blocked while PENDING
        assertSend(prEmail, pcCse.getId(), 400);

        // the addressed coordinator accepts
        mockMvc.perform(put("/api/contact-requests/" + requestId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user(pcCse.getEmail()))
                        .content(body(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk());

        // now allowed
        assertSend(prEmail, pcCse.getId(), 201);
    }

    @Test
    @DisplayName("9: a RESOLVED request keeps the PR -> PC pair allowed")
    void resolvedRequestKeepsPairAllowed() throws Exception {
        ContactRequest contactRequest = ContactRequestGrantFixtures.request(repository, profileRepository, pr,
                pcCse, ContactRequestStatus.ACCEPTED);
        repository.saveAndFlush(contactRequest);

        assertSend(pr.getEmail(), pcCse.getId(), 201);

        contactRequest.setStatus(ContactRequestStatus.RESOLVED);
        repository.saveAndFlush(contactRequest);

        assertSend(pr.getEmail(), pcCse.getId(), 201);
    }

    @Test
    @DisplayName("9: a REJECTED request leaves the PR -> PC pair blocked")
    void rejectedRequestLeavesPairBlocked() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, pr, pcCse, ContactRequestStatus.REJECTED);

        assertSend(pr.getEmail(), pcCse.getId(), 400);
    }

    @Test
    @DisplayName("9: a PENDING request leaves the PR -> PC pair blocked")
    void pendingRequestLeavesPairBlocked() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, pr, pcCse, ContactRequestStatus.PENDING);

        assertSend(pr.getEmail(), pcCse.getId(), 400);
    }

    // -------------------------------------------------------------- malformed

    @Test
    @DisplayName("9: malformed cross-department ACCEPTED row does not unlock the pair")
    void malformedCrossDepartmentAcceptedRowStaysBlocked() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, pr, pcEce, ContactRequestStatus.ACCEPTED);

        assertSend(pr.getEmail(), pcEce.getId(), 400);
    }

    @Test
    @DisplayName("9: a malformed cross-department row does not unlock the reverse PC -> requester direction")
    void malformedCrossDepartmentAcceptedRowStaysBlockedInReverse() throws Exception {
        // studentEce is in ECE, pcCse is in CSE: the create path would refuse
        // this pair, and the pre-existing PC rule only allows same-department
        // recipients, so a 400 here proves the row granted nothing.
        User studentEce = ContactRequestGrantFixtures.user(userRepository, "apistudentEce", Role.STUDENT, ece);
        ContactRequestGrantFixtures.profile(profileRepository, studentEce);
        ContactRequestGrantFixtures.request(repository, profileRepository, studentEce, pcCse,
                ContactRequestStatus.ACCEPTED);

        assertSend(pcCse.getEmail(), studentEce.getId(), 400);
    }

    @Test
    @DisplayName("9: a target deactivated after acceptance stops granting and blocks the send")
    void targetDeactivatedAfterAcceptanceStopsGranting() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, pr, pcCse, ContactRequestStatus.ACCEPTED);
        assertSend(pr.getEmail(), pcCse.getId(), 201);

        pcCse.setActive(false);
        userRepository.saveAndFlush(pcCse);

        assertThat(contactRequestService.hasDirectMessagingPermission(pr.getId(), pcCse.getId())).isFalse();
        assertSend(pr.getEmail(), pcCse.getId(), 400);
    }

    @Test
    @DisplayName("9: a requester deactivated after acceptance stops granting in both directions")
    void requesterDeactivatedAfterAcceptanceStopsGranting() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, pr, pcCse, ContactRequestStatus.RESOLVED);
        assertSend(pcCse.getEmail(), pr.getId(), 201);

        pr.setActive(false);
        userRepository.saveAndFlush(pr);

        assertThat(contactRequestService.hasDirectMessagingPermission(pr.getId(), pcCse.getId())).isFalse();
        assertThat(contactRequestService.hasDirectMessagingPermission(pcCse.getId(), pr.getId())).isFalse();
    }

    @Test
    @DisplayName("9: malformed PO-target ACCEPTED row does not unlock the pair")
    void malformedPoTargetAcceptedRowStaysBlocked() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, pr, po, ContactRequestStatus.ACCEPTED);

        assertSend(pr.getEmail(), po.getId(), 400);
    }

    @Test
    @DisplayName("9: malformed STUDENT -> STUDENT row grants nothing, even though the send itself is allowed by the pre-existing student rule")
    void malformedStudentToStudentRowGrantsNothing() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, student, pr, ContactRequestStatus.ACCEPTED);

        // The centralized permission method refuses the pair...
        assertThat(contactRequestService.hasDirectMessagingPermission(student.getId(), pr.getId())).isFalse();
        assertThat(contactRequestService.hasDirectMessagingPermission(pr.getId(), student.getId())).isFalse();
        // ...and the send that succeeds below succeeds only because STUDENT may
        // always address a non-PO user, never because of this row.
        assertSend(student.getEmail(), pr.getId(), 201);
    }

    @Test
    @DisplayName("9: a malformed PC-as-requester row does not unlock a cross-department coordinator pair")
    void malformedPcAsRequesterRowStaysBlocked() throws Exception {
        // A profile attached to a PC user in another department: both the
        // requester-role and the department invariant are violated, and the
        // pre-existing PC rule denies the cross-department recipient anyway.
        User pcRequester = ContactRequestGrantFixtures.user(userRepository, "apipcRequester", Role.PC, ece);
        var profile = ContactRequestGrantFixtures.profile(profileRepository, pcRequester);
        ContactRequestGrantFixtures.request(repository, profile, pcCse, ContactRequestStatus.ACCEPTED);

        assertSend(pcRequester.getEmail(), pcCse.getId(), 400);
    }

    // --------------------------------------------- existing rules not weakened

    @Test
    @DisplayName("6: existing messaging rules still apply when no request exists")
    void existingRulesStillApply() throws Exception {
        // PR -> same-department student was already allowed before 7P.4
        assertSend(pr.getEmail(), student.getId(), 201);
        // STUDENT -> PC was already allowed before 7P.4
        assertSend(student.getEmail(), pcCse.getId(), 201);
        // STUDENT -> PO is refused by the pre-existing rule
        assertSend(student.getEmail(), po.getId(), 400);
        // PR -> PC remains refused while no accepted request exists
        assertSend(pr.getEmail(), pcCse.getId(), 400);
    }

    @Test
    @DisplayName("6: an accepted request does not widen a department or role audience")
    void acceptedRequestDoesNotWidenAudienceSends() throws Exception {
        ContactRequestGrantFixtures.request(repository, profileRepository, pr, pcCse, ContactRequestStatus.ACCEPTED);

        // Audience sends never consult the contact-request exception, so a PR
        // still cannot address the whole PC role of a department.
        mockMvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
                        .with(user(pr.getEmail()))
                        .content(body(Map.of("title", "audience", "content", "x",
                                "departmentId", cse.getId(), "targetRole", "PC"))))
                .andExpect(status().isBadRequest());
    }
}

