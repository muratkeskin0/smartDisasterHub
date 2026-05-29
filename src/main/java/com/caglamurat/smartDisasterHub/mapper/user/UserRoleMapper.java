package com.caglamurat.smartDisasterHub.mapper.user;

import com.caglamurat.smartDisasterHub.domain.UserRole;
import com.caglamurat.smartDisasterHub.dto.user.UserRoleDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserRoleMapper {

    public UserRoleDTO toDTO(UserRole userRole) {
        if (userRole == null) {
            return null;
        }
        
        return UserRoleDTO.builder()
                .id(userRole.getId())
                .name(userRole.getName().name()) // Convert enum to String
                .description(userRole.getDescription())
                .createdAt(userRole.getCreatedAt())
                .updatedAt(userRole.getUpdatedAt())
                .build();
    }

    public List<UserRoleDTO> toDTOList(List<UserRole> userRoles) {
        if (userRoles == null) {
            return null;
        }
        
        return userRoles.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}

