package com.college.placement.auth;

import com.college.placement.auth.dto.RegisterRequest;
import com.college.placement.auth.dto.RegisterResponse;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse registerStudent(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered.");
        }

        String registerNumber = request.getRegisterNumber().trim();
        if (studentProfileRepository.existsByRegisterNumber(registerNumber)) {
            throw new ConflictException("Register number already exists.");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new BadRequestException("Department unavailable."));
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new BadRequestException("Department unavailable.");
        }

        // Public registration always creates a STUDENT account; role is never
        // controlled by the client.
        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.STUDENT)
                .department(department)
                .active(true)
                .build();
        user = userRepository.save(user);

        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .registerNumber(registerNumber)
                .batch(request.getBatch())
                .build();
        studentProfileRepository.save(profile);

        log.info("New student registered: {} ({})", email, registerNumber);

        return RegisterResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .departmentId(department.getId())
                .departmentName(department.getName())
                .build();
    }
}
