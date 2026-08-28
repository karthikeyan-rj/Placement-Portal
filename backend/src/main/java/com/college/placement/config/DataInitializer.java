package com.college.placement.config;

import com.college.placement.common.enums.Role;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.department.PrConfig;
import com.college.placement.department.PrConfigRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
@Slf4j
@RequiredArgsConstructor
@Order(1)
public class DataInitializer {

    @Bean
    CommandLineRunner initSeedData(UserRepository userRepository,
                                   DepartmentRepository departmentRepository,
                                   PrConfigRepository prConfigRepository,
                                   PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) {
                boolean hasStudents = userRepository.findByRole(Role.STUDENT).size() > 0;
                boolean hasPcs = userRepository.findByRole(Role.PC).size() > 0;
                if (hasStudents && hasPcs) {
                    log.info("Seed data already exists. Skipping initialization.");
                    return;
                }
                log.info("Partial seed data found. Completing initialization...");
            }

            log.info("Initializing seed data...");

            // Create departments (skip if already exist)
            List<String> deptNames = List.of(
                "CSE", "ECE", "EEE", "IT", "MECHANICAL", "CIVIL", "CSBS", "CSE-AIML", "MECHATRONICS"
            );
            for (String deptName : deptNames) {
                if (departmentRepository.findByNameIgnoreCase(deptName).isEmpty()) {
                    Department dept = Department.builder().name(deptName).build();
                    departmentRepository.save(dept);
                }
            }

            // Create PO (skip if already exists)
            if (userRepository.findByEmail("po@example.com").isEmpty()) {
                User po = User.builder()
                        .name("Placement Officer")
                        .email("po@example.com")
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .role(Role.PO)
                        .active(true)
                        .build();
                userRepository.save(po);
            }

            // Create PCs (skip if already exist)
            Department cse = departmentRepository.findByNameIgnoreCase("CSE").orElseThrow();
            Department ece = departmentRepository.findByNameIgnoreCase("ECE").orElseThrow();

            if (userRepository.findByEmail("pc.cse1@example.com").isEmpty()) {
                User pcCse1 = User.builder()
                        .name("PC CSE 1")
                        .email("pc.cse1@example.com")
                        .passwordHash(passwordEncoder.encode("pc123"))
                        .role(Role.PC)
                        .department(cse)
                        .active(true)
                        .build();
                userRepository.save(pcCse1);
            }

            if (userRepository.findByEmail("pc.cse2@example.com").isEmpty()) {
                User pcCse2 = User.builder()
                        .name("PC CSE 2")
                        .email("pc.cse2@example.com")
                        .passwordHash(passwordEncoder.encode("pc123"))
                        .role(Role.PC)
                        .department(cse)
                        .active(true)
                        .build();
                userRepository.save(pcCse2);
            }

            if (userRepository.findByEmail("pc.ece1@example.com").isEmpty()) {
                User pcEce1 = User.builder()
                        .name("PC ECE 1")
                        .email("pc.ece1@example.com")
                        .passwordHash(passwordEncoder.encode("pc123"))
                        .role(Role.PC)
                        .department(ece)
                        .active(true)
                        .build();
                userRepository.save(pcEce1);
            }

            if (userRepository.findByEmail("pc.ece2@example.com").isEmpty()) {
                User pcEce2 = User.builder()
                        .name("PC ECE 2")
                        .email("pc.ece2@example.com")
                        .passwordHash(passwordEncoder.encode("pc123"))
                        .role(Role.PC)
                        .department(ece)
                        .active(true)
                        .build();
                userRepository.save(pcEce2);
            }

            // Create Students (skip if already exist)
            if (userRepository.findByEmail("student1@example.com").isEmpty()) {
                User student1 = User.builder()
                        .name("Student One")
                        .email("student1@example.com")
                        .passwordHash(passwordEncoder.encode("student123"))
                        .role(Role.STUDENT)
                        .department(cse)
                        .active(true)
                        .build();
                userRepository.save(student1);
            }

            if (userRepository.findByEmail("student2@example.com").isEmpty()) {
                User student2 = User.builder()
                        .name("Student Two")
                        .email("student2@example.com")
                        .passwordHash(passwordEncoder.encode("student123"))
                        .role(Role.STUDENT)
                        .department(cse)
                        .active(true)
                        .build();
                userRepository.save(student2);
            }

            if (userRepository.findByEmail("student3@example.com").isEmpty()) {
                User student3 = User.builder()
                        .name("Student Three")
                        .email("student3@example.com")
                        .passwordHash(passwordEncoder.encode("student123"))
                        .role(Role.STUDENT)
                        .department(ece)
                        .active(true)
                        .build();
                userRepository.save(student3);
            }

            // Create PR configs for departments (skip if already exist)
            for (Department dept : departmentRepository.findByActiveTrue()) {
                if (prConfigRepository.findByDepartmentId(dept.getId()).isEmpty()) {
                    PrConfig prConfig = PrConfig.builder()
                            .department(dept)
                            .maxPrs(5)
                            .build();
                    prConfigRepository.save(prConfig);
                }
            }

            log.info("Seed data initialized successfully.");
            log.info("PO: po@example.com / admin123");
            log.info("PC CSE: pc.cse1@example.com / pc123");
            log.info("PC ECE: pc.ece1@example.com / pc123");
            log.info("Student CSE: student1@example.com / student123");
            log.info("Student ECE: student3@example.com / student123");
        };
    }
}
