package com.college.placement.config;

import com.college.placement.common.enums.CompanyType;
import com.college.placement.common.enums.InterviewStatus;
import com.college.placement.common.enums.PlacementDriveStatus;
import com.college.placement.common.enums.PlacementStatus;
import com.college.placement.common.enums.Role;
import com.college.placement.company.Company;
import com.college.placement.company.CompanyRepository;
import com.college.placement.department.Department;
import com.college.placement.department.DepartmentRepository;
import com.college.placement.department.PrConfig;
import com.college.placement.department.PrConfigRepository;
import com.college.placement.placement.EligibilityCriteria;
import com.college.placement.placement.EligibilityCriteriaRepository;
import com.college.placement.placement.PlacementDrive;
import com.college.placement.placement.PlacementDriveRepository;
import com.college.placement.placement.PlacementRecord;
import com.college.placement.placement.PlacementRecordRepository;
import com.college.placement.placement.StudentInterview;
import com.college.placement.placement.StudentInterviewRepository;
import com.college.placement.student.StudentAcademic;
import com.college.placement.student.StudentAcademicRepository;
import com.college.placement.student.StudentPlacementInfo;
import com.college.placement.student.StudentPlacementInfoRepository;
import com.college.placement.student.StudentProfessional;
import com.college.placement.student.StudentProfessionalRepository;
import com.college.placement.student.StudentProfile;
import com.college.placement.student.StudentProfileRepository;
import com.college.placement.user.User;
import com.college.placement.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DEVELOPMENT-ONLY bulk demo-data seeder/cleaner.
 *
 * <p>Entered via {@code SEED_BULK_DEMO_DATA=true} (cleaner via
 * {@code SEED_BULK_DEMO_CLEANUP=true}); both default to {@code false} and are
 * never enabled on production. All created records live in the {@code bulk.}
 * email namespace / {@code Bulk } company-name prefix, and cleanup touches only
 * that namespace.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkSeedService {

    private static final String SEED_PASSWORD = "BulkSeed@123";
    private static final int PRS_PER_DEPT = 12;
    private static final int MAX_PCS_PER_DEPT = 2;
    private static final int COMPANY_TARGET = 40;
    private static final int DRIVE_TARGET = 75;

    private record DeptSpec(String name, String code, int students, String tag) {}

    private static final List<DeptSpec> DEPARTMENTS = List.of(
        new DeptSpec("CSE", "1042", 170, "cse"),
        new DeptSpec("ECE", "1062", 210, "ece"),
        new DeptSpec("EEE", "1052", 190, "eee"),
        new DeptSpec("IT", "2052", 205, "it"),
        new DeptSpec("MECHANICAL", "1142", 180, "mechanical"),
        new DeptSpec("CIVIL", "1032", 165, "civil"),
        new DeptSpec("CSBS", "2442", 185, "csbs"),
        new DeptSpec("CSE-AIML", "1482", 160, "cseaiml"),
        new DeptSpec("MECHATRONICS", "1152", 215, "mechatronics")
    );

    private static final List<String> GIVEN = List.of(
        "Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Karthik", "Rohit", "Sanjay", "Kiran",
        "Priya", "Ananya", "Divya", "Meera", "Nithya", "Harini", "Lakshmi", "Sneha", "Anjali", "Kavya",
        "Abhinav", "Rahul", "Nikhil", "Varun", "Ganesh", "Manoj", "Ravi", "Suresh", "Prakash", "Deepak",
        "Keerthana", "Shreya", "Bhavya", "Pooja", "Swathi", "Madhavi", "Ranjani", "Varshini", "Aishwarya", "Sindhu");
    private static final List<String> SURNAMES = List.of(
        "Kumar", "Rajan", "Sundaram", "Iyer", "Rao", "Nair", "Pillai", "Menon", "Subramanian", "Krishnan",
        "Venkatesh", "Anand", "Sharma", "Gupta", "Patel", "Reddy", "Naidu", "Choudhary", "Mehta", "Jain",
        "Bose", "Dutta", "Sen", "Das", "Ghosh", "Banerjee", "Mukherjee", "Verma", "Agarwal", "Pandey",
        "Murugan", "Kannan", "Sekar", "Gopal", "Natarajan", "Balaji", "Srinivasan", "Ramesh", "Arun", "Selvam");

    private static final List<String> COMPANY_PRE = List.of(
        "Nimbus", "Crestline", "BlueOak", "Aurora", "Zenith", "Vertex", "Quantum", "Terra", "Lumina", "Stratos",
        "Ironclad", "Cedar", "Orbit", "Echo", "Riverstone", "Cobalt", "Vivid", "Momentum", "Solstice", "Windsong");
    private static final List<String> COMPANY_SUF = List.of(
        "Technologies", "Systems", "Solutions", "Dataworks", "Labs", "Industries", "Infotech", "Analytics", "Innovations", "Works");

    private static final List<String> ROLES = List.of(
        "Software Engineer", "Associate Software Engineer", "Data Analyst", "Systems Engineer",
        "Embedded Engineer", "Support Engineer", "Quality Analyst", "DevOps Engineer",
        "Full Stack Developer", "Backend Developer", "Frontend Developer", "Machine Learning Engineer",
        "Data Engineer", "Network Engineer", "Digital Engineer", "Graduate Trainee");

    private static final List<String> LOCATIONS = List.of(
        "Coimbatore", "Chennai", "Bengaluru", "Hyderabad", "Pune", "Remote");

    private static final List<String> SKILLS = List.of(
        "Java", "Python", "SQL", "C", "C++", "Data Structures", "Algorithms", "Machine Learning",
        "Spring Boot", "React", "JavaScript", "TypeScript", "MongoDB", "PostgreSQL", "AWS", "Docker",
        "Git", "Linux", "Networking", "IoT", "REST APIs", "Kubernetes", "Flutter", "Android");
    private static final List<String> CERTS = List.of(
        "AWS Certified Cloud Practitioner", "NPTEL Cloud Computing", "Certified Java Programmer",
        "Python for Data Science (Coursera)", "EC Council Ethical Hacking", "Microsoft Azure Fundamentals");
    private static final List<String> PROJECTS = List.of(
        "Smart Attendance Tracker", "E-Commerce Platform (Spring Boot)", "Vehicle Health Monitor (IoT)",
        "Campus Placement Analytics", "AI Chatbot for College Queries", "Warehouse Inventory System");

    private static final List<String> ROUNDS = List.of(
        "Aptitude", "Technical Round 1", "Technical Round 2", "HR Round", "Group Discussion", "Coding Test");

    private final DepartmentRepository departmentRepository;
    private final PrConfigRepository prConfigRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository profileRepository;
    private final StudentAcademicRepository academicRepository;
    private final StudentProfessionalRepository professionalRepository;
    private final StudentPlacementInfoRepository placementInfoRepository;
    private final CompanyRepository companyRepository;
    private final PlacementDriveRepository driveRepository;
    private final EligibilityCriteriaRepository criteriaRepository;
    private final PlacementRecordRepository recordRepository;
    private final StudentInterviewRepository interviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------
    // Deterministic pseudo-random source (stable across runs/JVMs)
    // ------------------------------------------------------------------

    private static long mix(long x) {
        x ^= x >>> 12;
        x *= 0x2545F4914F6CDD1DL;
        x ^= x >>> 27;
        x *= 0x9E3779B97F4A7C15L;
        x ^= x >>> 31;
        return x;
    }

    private double rand(long i) {
        long v = mix(i + 0x9E3779B97F4A7C15L);
        return (v & 0xFFFFFFFFL) / (double) 0x100000000L;
    }

    private int randInt(long i, int bound) {
        int b = Math.max(1, bound);
        return (int) (rand(i) * b);
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private double pickBetween(long i, double lo, double hi) {
        return lo + rand(i) * (hi - lo);
    }

    // ------------------------------------------------------------------
    // Seed
    // ------------------------------------------------------------------

    @Transactional
    public int seed() {
        long t0 = System.currentTimeMillis();
        if (userRepository.findByEmail("bulk.po@example.com").isPresent()) {
            log.info("Bulk demo data already present. Idempotent re-run; no duplicates created.");
            return 0;
        }

        log.info("SEED_BULK_DEMO_DATA=true — seeding bulk demo data (dev-only).");

        Map<String, Department> deptByName = departmentRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(d -> d.getName().toUpperCase(), Function.identity()));

        Set<String> existingRegisters = profileRepository.findAll().stream()
                .map(StudentProfile::getRegisterNumber).collect(Collectors.toSet());

        String pwHash = passwordEncoder.encode(SEED_PASSWORD);

        createUser("bulk.po@example.com", "Bulk Placement Officer", Role.PO, null, pwHash);

        List<Company> companies = new ArrayList<>();
        for (int i = 0; i < COMPANY_TARGET; i++) {
            String name = "Bulk " + COMPANY_PRE.get(i % COMPANY_PRE.size()) + " "
                    + COMPANY_SUF.get((i % COMPANY_SUF.size() + (i / COMPANY_PRE.size()) * (COMPANY_SUF.size() / 2)) % COMPANY_SUF.size());
            if (companyRepository.existsByNameIgnoreCase(name)) continue;
            Company c = Company.builder()
                    .name(name)
                    .description("Fictional development-environment company used for bulk test data.")
                    .companyType(CompanyType.values()[i % CompanyType.values().length])
                    .website("https://www." + name.toLowerCase().replaceAll("[^a-z0-9]+", "") + ".example.com")
                    .active(true).build();
            companies.add(companyRepository.save(c));
        }
        log.info("  companies created: {}", companies.size());

        List<DriveSample> driveSamples = seedDrives(companies, deptByName);

        List<BulkStudent> students = new ArrayList<>();
        int totalStudents = 0;
        int placedCount = 0;
        int interestedCount = 0;

        for (DeptSpec spec : DEPARTMENTS) {
            Department dept = deptByName.get(spec.name());
            if (dept == null) {
                log.warn("  department {} not present in DB; skipping", spec.name());
                continue;
            }
            for (int k = 0; k < spec.students(); k++) {
                long seed = spec.students() * 10_003L + spec.code().hashCode() * 1_000_003L + k;
                String register = "24039177" + spec.code() + String.format("%04d", 9001 + k);
                String email = "bulk.student." + spec.tag() + String.format("%04d", k + 1) + "@example.com";
                if (userRepository.existsByEmail(email) || existingRegisters.contains(register)) continue;

                String name = GIVEN.get(randInt(seed, GIVEN.size())) + " " + SURNAMES.get(randInt(seed + 1, SURNAMES.size()));
                User user = userRepository.save(User.builder().name(name).email(email).passwordHash(pwHash)
                        .role(Role.STUDENT).department(dept).active(rand(seed + 2) > 0.02).build());

                String section = rand(seed + 3) < 0.7 ? new String[]{"A", "B", "C"}[randInt(seed + 3, 3)] : null;
                String phone = rand(seed + 4) < 0.85 ? "98" + String.format("%08d", Math.abs((seed * 7919) % 100_000_000L)) : null;
                LocalDate dob = LocalDate.of(2004 + randInt(seed + 5, 2), 1 + randInt(seed + 6, 12), 1 + randInt(seed + 7, 28));

                StudentProfile profile = profileRepository.save(StudentProfile.builder().user(user).registerNumber(register)
                        .phone(phone).dateOfBirth(dob).batch("2024-2028").section(section).build());

                double rCgpa = rand(seed + 8);
                double cgpa = Math.min(9.8, Math.max(5.5, 5.5 + rCgpa * 4.3));
                int activeBacklogs = rCgpa < 0.7 ? 0 : rCgpa < 0.9 ? 1 : rCgpa < 0.98 ? 2 : 3;
                int historyBacklogs = rand(seed + 9) < 0.5 ? 0 : Math.min(activeBacklogs + 1, 4);
                boolean hasDiploma = rand(seed + 10) < 0.08;

                academicRepository.save(StudentAcademic.builder().studentProfile(profile)
                        .tenthPercentage(bd(pickBetween(seed + 11, 65, 98)))
                        .twelfthPercentage(bd(pickBetween(seed + 12, 65, 98)))
                        .diplomaPercentage(hasDiploma ? bd(pickBetween(seed + 13, 60, 95)) : null)
                        .cgpa(bd(cgpa)).activeBacklogs(activeBacklogs).historyOfBacklogs(historyBacklogs).build());

                StringBuilder skillStr = new StringBuilder();
                if (rand(seed + 18) < 0.8) {
                    for (int s = 0; s < 3 + randInt(seed + 19, 4); s++) {
                        if (s > 0) skillStr.append(", ");
                        skillStr.append(SKILLS.get((int) Math.abs((seed * 31 + s * 7) % SKILLS.size())));
                    }
                }
                professionalRepository.save(StudentProfessional.builder().studentProfile(profile)
                        .skills(skillStr.length() > 0 ? skillStr.toString() : null)
                        .certifications(rand(seed + 20) < 0.3 ? CERTS.get((int) Math.abs(seed % CERTS.size())) : null)
                        .projects(rand(seed + 21) < 0.35 ? PROJECTS.get((int) Math.abs((seed * 3) % PROJECTS.size())) : null)
                        .resumeUrl(rand(seed + 17) < 0.35 ? "https://bulk.example.com/resume/" + spec.tag() + "/" + (9001 + k) + ".pdf" : null)
                        .githubUrl(rand(seed + 14) < 0.45 ? "https://github.com/bulk-" + spec.tag() + "-" + (9001 + k) : null)
                        .linkedinUrl(rand(seed + 15) < 0.55 ? "https://linkedin.com/in/bulk-" + spec.tag() + "-" + (9001 + k) : null)
                        .portfolioUrl(rand(seed + 16) < 0.25 ? "https://bulk-" + spec.tag() + "-" + (9001 + k) + ".example.com" : null)
                        .build());

                boolean interested = rand(seed + 22) < 0.85;
                boolean placed = interested && rand(seed + 23) < 0.20;
                Company placedCompany = null;
                BigDecimal pkg = null;
                if (placed) {
                    placedCompany = companies.get((int) Math.abs(seed % companies.size()));
                    pkg = bd(pickBetween(seed + 24, 4.0, 24.0));
                }
                placementInfoRepository.save(StudentPlacementInfo.builder().studentProfile(profile)
                        .placementInterested(interested)
                        .placementStatus(placed ? PlacementStatus.PLACED : PlacementStatus.NOT_PLACED)
                        .placedCompany(placedCompany).packageLpa(pkg).interviewsAttended(0).build());

                students.add(new BulkStudent(user, profile, dept, 9001L + k, interested, placed));
                if (placed) placedCount++;
                if (interested) interestedCount++;
                totalStudents++;
            }
            try { entityManager.flush(); entityManager.clear(); } catch (Exception ex) { log.warn("  flush warning: {}", ex.getMessage()); }
        }
        log.info("  students: {} (interested {} / placed {})", totalStudents, interestedCount, placedCount);

        int interviewCount = 0;
        int recordCount = 0;
        for (BulkStudent bs : students) {
            if (!bs.interested) continue;
            long seed = bs.seq * 131L + bs.dept.getId();

            if (bs.placed) {
                DriveSample chosen = pickDrive(driveSamples, bs.dept.getId(), bs);
                if (chosen == null) continue;
                recordRepository.save(PlacementRecord.builder()
                        .studentProfile(bs.profile).company(chosen.drive().getCompany())
                        .placementDrive(chosen.drive()).packageLpa(chosen.drive().getPackageLpa())
                        .placementDate(LocalDate.now().minusDays(randInt(seed + 1, 40)))
                        .status("PLACED").build());
                recordCount++;
                interviewRepository.save(StudentInterview.builder()
                        .studentProfile(bs.profile).placementDrive(chosen.drive())
                        .roundName(ROUNDS.get(randInt(seed + 2, ROUNDS.size())) + " (Final)")
                        .status(InterviewStatus.SELECTED).attended(true)
                        .remarks("Selected through bulk demo drive.")
                        .interviewDate(LocalDate.now().minusDays(randInt(seed + 3, 45))).build());
                interviewCount++;
            } else if (rand(seed + 4) < 0.25) {
                int cnt = rand(seed + 5) < 0.5 ? 1 : 2;
                for (int r = 0; r < cnt; r++) {
                    DriveSample ds = pickDrive(driveSamples, bs.dept.getId(), bs);
                    if (ds == null) continue;
                    double rv = rand(seed + 6 + r);
                    InterviewStatus st = rv < 0.25 ? InterviewStatus.SCHEDULED
                            : rv < 0.40 ? InterviewStatus.ATTENDED
                            : rv < 0.55 ? InterviewStatus.PASSED
                            : rv < 0.85 ? InterviewStatus.REJECTED
                            : InterviewStatus.WITHDRAWN;
                    boolean attended = st == InterviewStatus.ATTENDED || st == InterviewStatus.PASSED
                            || st == InterviewStatus.REJECTED || st == InterviewStatus.SELECTED;
                    LocalDate d = st == InterviewStatus.SCHEDULED
                            ? LocalDate.now().plusDays(2 + randInt(seed + 7 + r, 20))
                            : LocalDate.now().minusDays(randInt(seed + 8 + r, 50));
                    interviewRepository.save(StudentInterview.builder()
                            .studentProfile(bs.profile).placementDrive(ds.drive())
                            .roundName(ROUNDS.get(randInt(seed + 9 + r, ROUNDS.size())))
                            .status(st).attended(attended).interviewDate(d)
                            .remarks(attended && st != InterviewStatus.SELECTED ? "Bulk demo interview result recorded." : null)
                            .build());
                    interviewCount++;
                    if (attended) {
                        jdbcTemplate.update("UPDATE student_placement_info SET interviews_attended = interviews_attended + 1 WHERE student_profile_id = ?",
                                bs.profile().getId());
                    }
                }
            }
        }
        log.info("  interviews: {} / placement records: {}", interviewCount, recordCount);

        int pcCreated = 0;
        for (DeptSpec spec : DEPARTMENTS) {
            Department dept = deptByName.get(spec.name());
            if (dept == null) continue;
            long existingPc = userRepository.countByRoleAndDepartment(Role.PC, dept.getId());
            for (int n = (int) existingPc + 1; n <= MAX_PCS_PER_DEPT; n++) {
                if (createUser("bulk.pc." + spec.tag() + n + "@example.com", "Bulk PC " + spec.name() + " " + n, Role.PC, dept, pwHash)) {
                    pcCreated++;
                }
            }
        }
        log.info("  PCs created: {}", pcCreated);

        int prCreated = 0;
        for (DeptSpec spec : DEPARTMENTS) {
            Department dept = deptByName.get(spec.name());
            if (dept == null) continue;
            List<BulkStudent> deptStudents = students.stream()
                    .filter(s -> s.dept().getId().equals(dept.getId()))
                    .sorted((a, b) -> Long.compare(a.seq(), b.seq()))
                    .toList();
            List<BulkStudent> prs = deptStudents.size() >= PRS_PER_DEPT
                    ? deptStudents.subList(0, PRS_PER_DEPT) : deptStudents;
            for (int p = 0; p < prs.size(); p++) {
                User u = prs.get(p).user();
                String prEmail = "bulk.pr." + spec.tag() + String.format("%02d", p + 1) + "@example.com";
                if (userRepository.existsByEmail(prEmail)) continue;
                u.setRole(Role.PR);
                u.setEmail(prEmail);
                userRepository.save(u);
                prCreated++;
            }
        }
        log.info("  PRs: {}", prCreated);

        int configUpdated = 0;
        for (Department dept : departmentRepository.findByActiveTrue()) {
            PrConfig cfg = prConfigRepository.findByDepartmentId(dept.getId())
                    .orElseGet(() -> PrConfig.builder().department(dept).maxPrs(5).build());
            if (cfg.getMaxPrs() != PRS_PER_DEPT) {
                cfg.setMaxPrs(PRS_PER_DEPT);
                prConfigRepository.save(cfg);
                configUpdated++;
            }
        }
        log.info("  pr_config departments raised to maxPrs={}: {}", PRS_PER_DEPT, configUpdated);

        long dt = System.currentTimeMillis() - t0;
        log.info("Bulk demo data seeded in {} ms. Students={}, Placed={}, PRs={}, PCs created={}, Companies={}, Drives={}",
                dt, totalStudents, placedCount, prCreated, pcCreated, companies.size(), driveSamples.size());
        log.info("DEV-ONLY: all bulk accounts share one internal dev seed password. Do not reuse for real accounts.");
        return totalStudents + placedCount + prCreated + pcCreated + companies.size() + driveSamples.size();
    }

    // ------------------------------------------------------------------
    // Cleanup (bulk namespace only)
    // ------------------------------------------------------------------

    @Transactional
    public int cleanup() {
        log.info("BULK DEMO CLEANUP requested (SEED_BULK_DEMO_CLEANUP=true). Removing ONLY bulk-seed records...");
        int removed = 0;
        removed += jdbcTemplate.update("DELETE FROM placement_records WHERE company_id IN (SELECT id FROM companies WHERE name LIKE 'Bulk %')"
                + " OR student_profile_id IN (SELECT sp.id FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM student_interviews WHERE placement_drive_id IN (SELECT id FROM placement_drives WHERE company_id IN (SELECT id FROM companies WHERE name LIKE 'Bulk %'))"
                + " OR student_profile_id IN (SELECT sp.id FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM eligibility_allowed_departments WHERE eligibility_criteria_id IN (SELECT ec.id FROM eligibility_criteria ec JOIN placement_drives pd ON pd.id = ec.placement_drive_id JOIN companies c ON c.id = pd.company_id WHERE c.name LIKE 'Bulk %')");
        removed += jdbcTemplate.update("DELETE FROM eligibility_criteria WHERE placement_drive_id IN (SELECT id FROM placement_drives WHERE company_id IN (SELECT id FROM companies WHERE name LIKE 'Bulk %'))");
        removed += jdbcTemplate.update("DELETE FROM placement_drives WHERE company_id IN (SELECT id FROM companies WHERE name LIKE 'Bulk %')");
        removed += jdbcTemplate.update("DELETE FROM student_access_codes WHERE student_profile_id IN (SELECT sp.id FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM pr_assignments WHERE student_profile_id IN (SELECT sp.id FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM student_professionals WHERE student_profile_id IN (SELECT sp.id FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM student_academics WHERE student_profile_id IN (SELECT sp.id FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM student_placement_info WHERE student_profile_id IN (SELECT sp.id FROM student_profiles sp JOIN users u ON u.id = sp.user_id WHERE u.email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM student_profiles WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'bulk.%')");
        removed += jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'bulk.%'");
        removed += jdbcTemplate.update("DELETE FROM companies WHERE name LIKE 'Bulk %'");
        removed += jdbcTemplate.update("UPDATE pr_config SET max_prs = 5 WHERE max_prs = 12");
        log.info("BULK DEMO CLEANUP complete. {} bulk-seed rows removed (non-bulk data untouched).", removed);
        return removed;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean createUser(String email, String name, Role role, Department dept, String pwHash) {
        if (userRepository.existsByEmail(email)) return false;
        userRepository.save(User.builder().name(name).email(email).passwordHash(pwHash)
                .role(role).department(dept).active(true).build());
        return true;
    }

    private List<DriveSample> seedDrives(List<Company> companies, Map<String, Department> deptByName) {
        List<Department> depts = new ArrayList<>(deptByName.values());
        List<DriveSample> samples = new ArrayList<>();
        int[] counts = { 15, 15, 12, 12, 18, 3 }; // UPCOMING, REGISTRATION_OPEN, REGISTRATION_CLOSED, ONGOING, COMPLETED, CANCELLED
        PlacementDriveStatus[] statuses = PlacementDriveStatus.values();
        int idx = 0;
        for (int s = 0; s < statuses.length; s++) {
            for (int j = 0; j < counts[s]; j++) {
                Company company = companies.get(idx % companies.size());
                long seed = idx * 131L + s * 97L;
                LocalDate driveDate;
                LocalDate deadline;
                switch (statuses[s]) {
                    case UPCOMING -> {
                        driveDate = LocalDate.now().plusDays(30 + randInt(seed + 1, 60));
                        deadline = driveDate.minusDays(15 + randInt(seed + 2, 30));
                    }
                    case REGISTRATION_OPEN -> {
                        driveDate = LocalDate.now().plusDays(15 + randInt(seed + 1, 25));
                        deadline = LocalDate.now().plusDays(2 + randInt(seed + 2, 12));
                    }
                    case REGISTRATION_CLOSED -> {
                        driveDate = LocalDate.now().plusDays(5 + randInt(seed + 1, 20));
                        deadline = LocalDate.now().minusDays(2 + randInt(seed + 2, 12));
                    }
                    case ONGOING -> {
                        driveDate = LocalDate.now().minusDays(1);
                        deadline = driveDate.minusDays(10);
                    }
                    case COMPLETED -> {
                        driveDate = LocalDate.now().minusDays(10 + randInt(seed + 1, 35));
                        deadline = driveDate.minusDays(15);
                    }
                    default -> {
                        driveDate = LocalDate.now().minusDays(randInt(seed + 1, 30));
                        deadline = driveDate.minusDays(5);
                    }
                }
                PlacementDrive drive = driveRepository.save(PlacementDrive.builder()
                        .company(company)
                        .jobRole(ROLES.get((int) Math.abs((seed * 31) % ROLES.size())))
                        .packageLpa(bd(pickBetween(seed + 3, 4.0, 24.0)))
                        .driveDate(driveDate).registrationDeadline(deadline)
                        .location(LOCATIONS.get((int) Math.abs(seed % LOCATIONS.size())))
                        .jobDescription("Bulk demo drive for " + company.getName() + " (development data only).")
                        .status(statuses[s]).build());

                double cgpaPick = rand(seed + 4);
                BigDecimal minCgpa = cgpaPick < 0.3 ? bd(6.0) : cgpaPick < 0.6 ? bd(7.0) : cgpaPick < 0.85 ? bd(7.5) : bd(8.0);
                int maxBack = randInt(seed + 5, 3);
                boolean allDepts = rand(seed + 6) < 0.45;
                Set<Department> allowed = new HashSet<>();
                if (allDepts) {
                    allowed.addAll(depts);
                } else {
                    int n = Math.min(2 + randInt(seed + 7, 4), depts.size());
                    for (int i = 0; i < n; i++) allowed.add(depts.get((int) Math.abs((seed + i * 13) % depts.size())));
                    if (allowed.size() < 2) allowed.add(depts.get((int) Math.abs(seed % depts.size())));
                }
                criteriaRepository.save(EligibilityCriteria.builder()
                        .placementDrive(drive).minCgpa(minCgpa).maxActiveBacklogs(maxBack)
                        .minTenthPct(rand(seed + 8) < 0.6 ? bd(60 + randInt(seed + 9, 15)) : null)
                        .minTwelfthPct(rand(seed + 10) < 0.5 ? bd(60 + randInt(seed + 11, 15)) : null)
                        .minDiplomaPct(rand(seed + 12) < 0.2 ? bd(55 + randInt(seed + 13, 10)) : null)
                        .allowedDepartments(allowed).build());

                samples.add(new DriveSample(drive, minCgpa, maxBack,
                        allowed.stream().map(Department::getId).collect(Collectors.toSet())));
                idx++;
            }
        }
        return samples;
    }

    private DriveSample pickDrive(List<DriveSample> samples, Long deptId, BulkStudent student) {
        List<DriveSample> eligible = samples.stream()
                .filter(d -> d.allowedDeptIds().contains(deptId))
                .filter(d -> !d.drive().getStatus().equals(PlacementDriveStatus.CANCELLED))
                .toList();
        if (eligible.isEmpty()) return null;
        long seed = student.seq() * 17L + deptId;
        return eligible.get((int) (Math.abs(seed) % eligible.size()));
    }

    private String placeholders(int count) {
        if (count <= 0) return "NULL";
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> "?").collect(Collectors.joining(","));
    }

    private record DriveSample(PlacementDrive drive, BigDecimal minCgpa, int maxBacklogs, Set<Long> allowedDeptIds) {}

    private record BulkStudent(User user, StudentProfile profile, Department dept, long seq, boolean interested, boolean placed) {}
}
