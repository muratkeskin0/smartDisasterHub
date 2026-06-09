package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.auth.ForgotPasswordRequest;
import com.caglamurat.smartDisasterHub.dto.auth.LoginRequest;
import com.caglamurat.smartDisasterHub.dto.auth.LoginResponse;
import com.caglamurat.smartDisasterHub.dto.auth.RegisterRequest;
import com.caglamurat.smartDisasterHub.dto.auth.RegisterResponse;
import com.caglamurat.smartDisasterHub.dto.auth.ResetPasswordRequest;
import com.caglamurat.smartDisasterHub.dto.auth.VerifyTokenResponse;
import com.caglamurat.smartDisasterHub.service.auth.IAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller for handling authentication operations
 * Delegates business logic to AuthService
 * 
 * Request/Response Pairs:
 * - POST /register: RegisterRequest → RegisterResponse
 * - POST /login: LoginRequest → LoginResponse  
 * - GET /verify: (Authorization header) → VerifyTokenResponse
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IAuthService authService;

    /**
     * Register endpoint
     * Creates a new user account with default USER role
     * 
     * @param registerRequest Registration details (firstName, lastName, email, password)
     * @return RegisterResponse with JWT token and user details
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("REST request to register user: {}", registerRequest.getEmail());
        RegisterResponse registerResponse = authService.register(registerRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", registerResponse));
    }

    /**
     * Login endpoint
     * Validates credentials and returns JWT token
     * 
     * @param loginRequest Login credentials (email and password)
     * @return LoginResponse with JWT token and user details
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("REST request to login user: {}", loginRequest.getEmail());
        LoginResponse loginResponse = authService.login(loginRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", loginResponse));
    }

    /**
     * Verify token endpoint
     * Checks if the provided token is valid and extracts token details
     * 
     * @param authHeader Authorization header containing Bearer token
     * @return VerifyTokenResponse with validation result and token details (email, role)
     */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<VerifyTokenResponse>> verifyToken(@RequestHeader("Authorization") String authHeader) {
        log.debug("REST request to verify token");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            VerifyTokenResponse verifyResponse = authService.verifyToken(token);
            
            String message = verifyResponse.isValid() ? "Token is valid" : "Token is invalid";
            return ResponseEntity.ok(ApiResponse.success(message, verifyResponse));
        }
        
        VerifyTokenResponse invalidResponse = VerifyTokenResponse.builder()
                .valid(false)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Token is invalid", invalidResponse));
    }

    /**
     * Logout endpoint
     * Since JWT tokens are stateless, logout is handled client-side
     * This endpoint just confirms the logout request
     * 
     * @return Success response
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        log.info("REST request to logout");
        // JWT tokens are stateless, so logout is primarily handled client-side
        // This endpoint exists for API consistency
        return ResponseEntity.ok(ApiResponse.success("Logout successful"));
    }

    @GetMapping("/activate")
    public ResponseEntity<ApiResponse<Void>> activateEmail(@RequestParam("token") String token) {
        log.info("REST request to activate email");
        String message = authService.activateEmail(token);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("REST request to forgot password for email: {}", request.getEmail());
        String message = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @GetMapping("/reset-password/validate")
    public ResponseEntity<ApiResponse<Void>> validateResetPasswordToken(@RequestParam("token") String token) {
        log.debug("REST request to validate password reset token");
        authService.validatePasswordResetToken(token);
        return ResponseEntity.ok(ApiResponse.success("Password reset link is valid"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("REST request to reset password");
        String message = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message));
    }
}

