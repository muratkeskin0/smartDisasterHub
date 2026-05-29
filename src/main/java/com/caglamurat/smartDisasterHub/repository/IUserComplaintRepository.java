package com.caglamurat.smartDisasterHub.repository;

import com.caglamurat.smartDisasterHub.domain.UserComplaint;
import com.caglamurat.smartDisasterHub.enums.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface IUserComplaintRepository extends JpaRepository<UserComplaint, Long> {

    @EntityGraph(attributePaths = {"submitter", "assignedStaff"})
    Page<UserComplaint> findBySubmitterIdOrderByCreatedAtDesc(Long submitterId, Pageable pageable);

    @EntityGraph(attributePaths = {"submitter", "assignedStaff"})
    Optional<UserComplaint> findWithDetailsById(Long id);

    @Query("""
            SELECT COUNT(c) FROM UserComplaint c
            WHERE c.status IN :activeStatuses AND c.assignedStaff IS NULL
            """)
    long countActiveUnassigned(@Param("activeStatuses") Collection<ComplaintStatus> activeStatuses);

    @Query("""
            SELECT COUNT(c) FROM UserComplaint c
            WHERE c.status IN :activeStatuses AND c.assignedStaff.id = :staffId
            """)
    long countActiveAssignedTo(
            @Param("activeStatuses") Collection<ComplaintStatus> activeStatuses,
            @Param("staffId") Long staffId);

    @Query("""
            SELECT COUNT(c) FROM UserComplaint c
            WHERE c.status IN :activeStatuses
            """)
    long countActive(@Param("activeStatuses") Collection<ComplaintStatus> activeStatuses);

    @EntityGraph(attributePaths = {"submitter", "assignedStaff"})
    @Query("""
            SELECT c FROM UserComplaint c
            WHERE c.status IN :activeStatuses AND c.assignedStaff IS NULL
            """)
    Page<UserComplaint> findActiveUnassigned(
            @Param("activeStatuses") Collection<ComplaintStatus> activeStatuses,
            Pageable pageable);

    @EntityGraph(attributePaths = {"submitter", "assignedStaff"})
    @Query("""
            SELECT c FROM UserComplaint c
            WHERE c.status IN :activeStatuses AND c.assignedStaff.id = :staffId
            """)
    Page<UserComplaint> findActiveAssignedTo(
            @Param("activeStatuses") Collection<ComplaintStatus> activeStatuses,
            @Param("staffId") Long staffId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"submitter", "assignedStaff"})
    @Query("""
            SELECT c FROM UserComplaint c
            WHERE c.status IN :activeStatuses
            """)
    Page<UserComplaint> findActive(
            @Param("activeStatuses") Collection<ComplaintStatus> activeStatuses,
            Pageable pageable);

    @Query("""
            SELECT COUNT(c) FROM UserComplaint c
            WHERE c.status IN :statuses
            """)
    long countByStatusIn(@Param("statuses") Collection<ComplaintStatus> statuses);

    @EntityGraph(attributePaths = {"submitter", "assignedStaff"})
    @Query("""
            SELECT c FROM UserComplaint c
            WHERE c.status IN :statuses
            """)
    Page<UserComplaint> findByStatusIn(
            @Param("statuses") Collection<ComplaintStatus> statuses,
            Pageable pageable);
}
