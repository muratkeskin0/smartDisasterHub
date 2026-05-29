package com.caglamurat.smartDisasterHub.repository;

import com.caglamurat.smartDisasterHub.domain.UserRole;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IUserRoleRepository extends JpaRepository<UserRole, Long> {

    Optional<UserRole> findByName(UserRoleType name);

    boolean existsByName(UserRoleType name);

}

