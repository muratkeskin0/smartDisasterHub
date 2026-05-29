package com.caglamurat.smartDisasterHub.mapper.user;

import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.domain.UserRole;
import com.caglamurat.smartDisasterHub.dto.auth.RegisterRequest;
import com.caglamurat.smartDisasterHub.dto.user.UserCreateDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final UserRoleMapper userRoleMapper;

    public UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }
        
        // Convert byte[] to Base64 string
        String profileImageBase64 = null;
        if (user.getProfileImage() != null && user.getProfileImage().length > 0) {
            profileImageBase64 = Base64.getEncoder().encodeToString(user.getProfileImage());
        }
        
        return UserDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(userRoleMapper.toDTO(user.getRole()))
                .isEmailVerified(user.getIsEmailVerified())
                .profileImageBase64(profileImageBase64)
                .profileImageContentType(user.getProfileImageContentType())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public User toEntity(UserCreateDTO createDTO, UserRole role) {
        if (createDTO == null) {
            return null;
        }
        
        // Convert Base64 string to byte[]
        byte[] profileImage = null;
        if (createDTO.getProfileImageBase64() != null && !createDTO.getProfileImageBase64().isEmpty()) {
            try {
                profileImage = Base64.getDecoder().decode(createDTO.getProfileImageBase64());
            } catch (IllegalArgumentException e) {
                // Invalid Base64, skip
                profileImage = null;
            }
        }
        
        return User.builder()
                .firstName(createDTO.getFirstName())
                .lastName(createDTO.getLastName())
                .email(createDTO.getEmail())
                .password(createDTO.getPassword())
                .role(role)
                .isEmailVerified(false)
                .profileImage(profileImage)
                .profileImageContentType(createDTO.getProfileImageContentType())
                .build();
    }

    public User toEntity(RegisterRequest registerRequest, UserRole role, String encodedPassword) {
        if (registerRequest == null) {
            return null;
        }
        
        return User.builder()
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .email(registerRequest.getEmail())
                .password(encodedPassword)
                .role(role)
                .isEmailVerified(false)
                .build();
    }

    public List<UserDTO> toDTOList(List<User> users) {
        if (users == null) {
            return null;
        }
        
        return users.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void updateEntityFromDTO(UserUpdateDTO dto, User user, UserRole role) {
        if (dto == null || user == null) {
            return;
        }
        
        if (dto.getFirstName() != null) {
            user.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            user.setLastName(dto.getLastName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPassword() != null) {
            user.setPassword(dto.getPassword());
        }
        if (role != null) {
            user.setRole(role);
        }
        
        // Update profile image if provided
        if (dto.getProfileImageBase64() != null) {
            if (dto.getProfileImageBase64().isEmpty()) {
                // Empty string = delete image
                user.setProfileImage(null);
                user.setProfileImageContentType(null);
            } else {
                try {
                    byte[] profileImage = Base64.getDecoder().decode(dto.getProfileImageBase64());
                    user.setProfileImage(profileImage);
                    user.setProfileImageContentType(dto.getProfileImageContentType());
                } catch (IllegalArgumentException e) {
                    // Invalid Base64, skip update
                }
            }
        }
        
        if (dto.getIsEmailVerified() != null) {
            user.setIsEmailVerified(dto.getIsEmailVerified());
        }
    }
}

