package com.college.placement.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentAccessCodeRepository extends JpaRepository<StudentAccessCode, Long> {

    Optional<StudentAccessCode> findByStudentProfileId(Long studentProfileId);

    boolean existsByStudentProfileId(Long studentProfileId);

    Optional<StudentAccessCode> findByRegisterNumber(String registerNumber);

    boolean existsByRegisterNumber(String registerNumber);

    Optional<StudentAccessCode> findByRegisterNumberAndUsedAtIsNull(String registerNumber);

    /**
     * Finds authorized-student rows whose register number ends with the given
     * suffix. Used to support short-form register numbers: the student may enter
     * a shorter college register number, and identity is matched on the trailing
     * digits of the CSV register number. Candidates are still re-verified in
     * {@link com.college.placement.auth.AuthService} via the last-5-numeric-digits
     * comparison before any code is accepted.
     */
    @Query("select sac from StudentAccessCode sac where sac.registerNumber like concat('%', :suffix)")
    List<StudentAccessCode> findByRegisterNumberEndingWith(@Param("suffix") String suffix);
}
