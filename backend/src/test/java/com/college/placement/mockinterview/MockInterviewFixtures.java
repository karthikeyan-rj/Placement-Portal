package com.college.placement.mockinterview;

import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Real, persisted rows for the Mock Interview tests.
 *
 * <p>Question content is deliberately NOT created here: the tests read the
 * existing certified preparation bank, which is the whole point of the feature.
 * Each user gets a unique email so nothing collides and no seed account is
 * touched.
 */
final class MockInterviewFixtures {

    private static final AtomicLong SEQ = new AtomicLong();

    private MockInterviewFixtures() {
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

    /** A STUDENT or PR account is identified by its StudentProfile, exactly as in production. */
    static StudentProfile studentProfile(StudentProfileRepository repository, User user) {
        return repository.save(StudentProfile.builder()
                .user(user)
                .registerNumber(unique("REG"))
                .build());
    }
}
