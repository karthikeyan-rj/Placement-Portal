package com.college.placement.staff;

import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds real, persisted rows for the staff-profile tests. Every user gets a
 * unique email so the parallel-free test transaction can never collide with
 * another fixture, and no seed/demo account is ever touched.
 */
public final class StaffProfileFixtures {

    private static final AtomicLong SEQ = new AtomicLong();

    private StaffProfileFixtures() {
    }

    private static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    public static Department department(DepartmentRepository repository, String name) {
        return repository.save(Department.builder()
                .name(name + "-" + SEQ.incrementAndGet())
                .active(true)
                .build());
    }

    public static User user(UserRepository repository, String label, Role role, Department department) {
        return repository.save(User.builder()
                .name(label)
                .email(unique(label.toLowerCase()) + "@example.test")
                .passwordHash("not-used-in-these-tests")
                .role(role)
                .department(department)
                .active(true)
                .build());
    }

    public static StaffProfile profile(StaffProfileRepository repository, User user, String designation) {
        return repository.save(StaffProfile.builder()
                .user(user)
                .designation(designation)
                .expertise(List.of("Java"))
                .build());
    }

    /** A STUDENT or PR user owns a StudentProfile instead, which must keep working. */
    public static StudentProfile studentProfile(StudentProfileRepository repository, User user) {
        return repository.save(StudentProfile.builder()
                .user(user)
                .registerNumber(unique("REG"))
                .build());
    }
}
