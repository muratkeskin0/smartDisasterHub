package com.caglamurat.smartDisasterHub.service.user;

import com.caglamurat.smartDisasterHub.domain.UserRole;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.repository.IUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserRoleService implements IUserRoleService {

    private final IUserRoleRepository _userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRole> findById(Long id) {
        log.debug("Finding role by ID: {}", id);
        return _userRoleRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRole> findByName(String name) {
        log.debug("Finding role by name: {}", name);
        try {
            UserRoleType roleType = UserRoleType.valueOf(name.toUpperCase());
            return _userRoleRepository.findByName(roleType);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role name: {}", name);
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRole> findAll() {
        log.debug("Finding all roles");
        return _userRoleRepository.findAll();
    }

    @Override
    public UserRole save(UserRole userRole) {
        log.debug("Saving role: {}", userRole.getName());
        
        // Role name validation
        if (userRole.getName() == null) {
            throw new BusinessException(
                ErrorCode.MISSING_REQUIRED_FIELD,
                "Role name is required"
            );
        }
        
        // Check for duplicate role name (for new roles)
        if (userRole.getId() == null && _userRoleRepository.existsByName(userRole.getName())) {
            throw new BusinessException(
                ErrorCode.ROLE_ALREADY_EXISTS,
                "Role already exists: " + userRole.getName()
            );
        }
        
        return _userRoleRepository.save(userRole);
    }

}

