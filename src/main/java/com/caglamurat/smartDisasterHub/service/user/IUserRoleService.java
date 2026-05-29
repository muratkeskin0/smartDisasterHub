package com.caglamurat.smartDisasterHub.service.user;

import com.caglamurat.smartDisasterHub.domain.UserRole;

import java.util.List;
import java.util.Optional;

public interface IUserRoleService {

    Optional<UserRole> findById(Long id);

    Optional<UserRole> findByName(String name);

    List<UserRole> findAll();

    UserRole save(UserRole userRole);
}

