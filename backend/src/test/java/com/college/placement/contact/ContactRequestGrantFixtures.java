package com.college.placement.contact;

import com.college.placement.common.enums.ContactRequestStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds real, persisted rows for the contact-request grant security tests.
 *
 * <p>Rows are written straight through the repositories, which deliberately
 * bypasses {@code ContactRequestService#createContactRequest}. That is the whole
 * point: the permission query has to hold even for combinations the create path
 * would never produce, such as a cross-department pair or a PO target.
 */
final class ContactRequestGrantFixtures {

    private static final AtomicLong SEQ = new AtomicLong();

    private ContactRequestGrantFixtures() {
    }

    private static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    static Department department(DepartmentRepository repository, String name) {
        return repository.save(Department.builder()
                .name(name + "-" + SEQ.incrementAndGet())
                .active(true)
                .build());
    }

    static User user(UserRepository repository, String label, Role role, Department department) {
        return repository.save(User.builder()
                .name(label)
                .email(unique(label.toLowerCase()) + "@example.test")
                .passwordHash("not-used-in-these-tests")
                .role(role)
                .department(department)
                .active(true)
                .build());
    }

    static StudentProfile profile(StudentProfileRepository repository, User user) {
        return repository.save(StudentProfile.builder()
                .user(user)
                .registerNumber(unique("REG"))
                .build());
    }

    /**
     * Convenience for the common valid shape: a STUDENT or PR requester with a
     * profile, addressed to a PC.
     */
    static ContactRequest request(ContactRequestRepository repository, StudentProfileRepository profiles,
                                  User requester, User target, ContactRequestStatus status) {
        return request(repository, profiles.findByUserId(requester.getId()).orElseThrow(), target, status);
    }

    static ContactRequest request(ContactRequestRepository repository, StudentProfile requesterProfile,
                                  User target, ContactRequestStatus status) {
        return repository.save(ContactRequest.builder()
                .studentProfile(requesterProfile)
                .targetUser(target)
                .subject("grant fixture")
                .message("grant fixture message")
                .status(status)
                .build());
    }
}
