package com.caglamurat.smartDisasterHub.security;

import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUserContext {

    private final IUserRepository userRepository;

    public String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getName();
    }

    public User requireCurrentUser() {
        String email = currentEmail();
        if (email == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    public boolean isAdmin() {
        return hasRole(UserRoleType.ADMIN);
    }

    public boolean isManager() {
        return hasRole(UserRoleType.MANAGER);
    }

    public boolean isStaff() {
        return isAdmin() || isManager();
    }

    private boolean hasRole(UserRoleType role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String expected = "ROLE_" + role.name();
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (expected.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
