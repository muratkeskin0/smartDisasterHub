package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.user.ManagerCreateDTO;
import com.caglamurat.smartDisasterHub.dto.user.PasswordUpdateDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserCreateDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserUpdateDTO;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.service.user.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final IUserService _userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@Valid @RequestBody UserCreateDTO createDTO) {
        log.info("REST request to create user with email: {}", createDTO.getEmail());
        UserDTO createdUser = _userService.createUser(createDTO);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", createdUser));
    }

    @PostMapping("/managers")
    public ResponseEntity<ApiResponse<UserDTO>> createManager(@Valid @RequestBody ManagerCreateDTO createDTO) {
        log.info("REST request to create manager with email: {}", createDTO.getEmail());
        UserDTO created = _userService.createManager(createDTO);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Manager created successfully", created));
    }

    @GetMapping("/managers")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getManagers() {
        return ResponseEntity.ok(ApiResponse.success(_userService.findManagers()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO updateDTO) {
        log.info("REST request to update user with ID: {}", id);
        UserDTO updatedUser = _userService.updateUser(id, updateDTO);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", updatedUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable Long id) {
        log.debug("REST request to get user by ID: {}", id);
        UserDTO user = _userService.findById(id)
                .orElseThrow(() -> com.caglamurat.smartDisasterHub.exception.ResourceNotFoundException.userNotFound(id));
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserByEmail(@PathVariable String email) {
        log.debug("REST request to get user by email: {}", email);
        UserDTO user = _userService.findByEmail(email)
                .orElseThrow(() -> com.caglamurat.smartDisasterHub.exception.ResourceNotFoundException.userNotFoundByEmail(email));
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers() {
        log.debug("REST request to get all users");
        List<UserDTO> users = _userService.findAll();
        return ResponseEntity.ok(ApiResponse.success(users));
    }


    @GetMapping("/verified")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getVerifiedUsers() {
        log.debug("REST request to get verified users");
        List<UserDTO> users = _userService.findVerifiedUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/role/{roleId}")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getUsersByRole(@PathVariable Long roleId) {
        log.debug("REST request to get users by role ID: {}", roleId);
        List<UserDTO> users = _userService.findUsersByRole(roleId);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserDTO>>> searchUsersByName(@RequestParam String term) {
        log.debug("REST request to search users by name: {}", term);
        List<UserDTO> users = _userService.searchUsersByName(term);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        log.info("REST request to delete user with ID: {}", id);
        _userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully"));
    }

    @PostMapping("/{id}/resend-activation")
    public ResponseEntity<ApiResponse<Void>> resendActivationEmail(@PathVariable Long id) {
        log.info("REST request to resend activation email for user ID: {}", id);
        _userService.resendActivationEmail(id);
        return ResponseEntity.ok(ApiResponse.success("Activation email sent"));
    }

    @GetMapping("/exists/email/{email}")
    public ResponseEntity<ApiResponse<Boolean>> checkEmailExists(@PathVariable String email) {
        log.debug("REST request to check if email exists: {}", email);
        boolean exists = _userService.existsByEmail(email);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<ApiResponse<UserDTO>> updatePassword(
            @PathVariable Long id,
            @Valid @RequestBody PasswordUpdateDTO passwordDTO) {
        log.info("REST request to update password for user ID: {}", id);
        
        // Şifre eşleşme kontrolü
        if (!passwordDTO.getNewPassword().equals(passwordDTO.getConfirmPassword())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "New password and confirm password do not match"
            );
        }
        
        UserDTO updatedUser = _userService.updatePassword(
            id, 
            passwordDTO.getOldPassword(), 
            passwordDTO.getNewPassword()
        );
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully", updatedUser));
    }

    @PatchMapping("/{id}/verify-email")
    public ResponseEntity<ApiResponse<UserDTO>> verifyEmail(@PathVariable Long id) {
        log.info("REST request to verify email for user ID: {}", id);
        UserDTO updatedUser = _userService.verifyEmail(id);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", updatedUser));
    }

    @PatchMapping("/{userId}/role/{roleId}")
    public ResponseEntity<ApiResponse<UserDTO>> changeUserRole(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        log.info("REST request to change role for user ID: {} to role ID: {}", userId, roleId);
        UserDTO updatedUser = _userService.changeUserRole(userId, roleId);
        return ResponseEntity.ok(ApiResponse.success("User role changed successfully", updatedUser));
    }

    @PostMapping(value = "/{id}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserDTO>> uploadProfileImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        log.info("REST request to upload profile image for user ID: {}", id);
        
        try {
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "File must be an image"
                );
            }
            
            // Validate file size (max 5MB)
            long maxSize = 5 * 1024 * 1024; // 5MB in bytes
            if (file.getSize() > maxSize) {
                throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "Image size must not exceed 5MB"
                );
            }
            
            // Convert file to UserUpdateDTO
            UserUpdateDTO updateDTO = UserUpdateDTO.builder()
                    .profileImageBase64(java.util.Base64.getEncoder().encodeToString(file.getBytes()))
                    .profileImageContentType(contentType)
                    .build();
            
            UserDTO updatedUser = _userService.updateUser(id, updateDTO);
            return ResponseEntity.ok(ApiResponse.success("Profile image uploaded successfully", updatedUser));
            
        } catch (IOException e) {
            log.error("Error reading uploaded file", e);
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Failed to process uploaded image"
            );
        }
    }

    @GetMapping("/{id}/profile-image")
    public ResponseEntity<byte[]> getProfileImage(@PathVariable Long id) {
        log.debug("REST request to get profile image for user ID: {}", id);
        
        UserDTO user = _userService.findById(id)
                .orElseThrow(() -> com.caglamurat.smartDisasterHub.exception.ResourceNotFoundException.userNotFound(id));
        
        if (user.getProfileImageBase64() == null) {
            return ResponseEntity.notFound().build();
        }
        
        byte[] imageBytes = java.util.Base64.getDecoder().decode(user.getProfileImageBase64());
        
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(user.getProfileImageContentType()))
                .body(imageBytes);
    }

    @DeleteMapping("/{id}/profile-image")
    public ResponseEntity<ApiResponse<UserDTO>> deleteProfileImage(@PathVariable Long id) {
        log.info("REST request to delete profile image for user ID: {}", id);
        
        UserUpdateDTO updateDTO = UserUpdateDTO.builder()
                .profileImageBase64("")  // Empty string to delete
                .build();
        
        UserDTO updatedUser = _userService.updateUser(id, updateDTO);
        return ResponseEntity.ok(ApiResponse.success("Profile image deleted successfully", updatedUser));
    }
}

