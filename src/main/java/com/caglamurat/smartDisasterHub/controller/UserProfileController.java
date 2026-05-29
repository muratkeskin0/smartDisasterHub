package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.user.ProfileUpdateDTO;
import com.caglamurat.smartDisasterHub.dto.user.ProfileUpdateResultDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import com.caglamurat.smartDisasterHub.service.user.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final IUserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> getProfile() {
        log.debug("REST request to get current user profile");
        return ResponseEntity.ok(ApiResponse.success(userService.getCurrentUserProfile()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(@Valid @RequestBody ProfileUpdateDTO updateDTO) {
        log.info("REST request to update current user profile");
        ProfileUpdateResultDTO result = userService.updateCurrentUserProfile(updateDTO);
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result.getUser()));
    }

    @DeleteMapping("/profile/pending-email")
    public ResponseEntity<ApiResponse<UserDTO>> cancelPendingEmailChange() {
        log.info("REST request to cancel pending email change");
        UserDTO user = userService.cancelPendingEmailChange();
        return ResponseEntity.ok(ApiResponse.success("Pending email change cancelled", user));
    }
}
