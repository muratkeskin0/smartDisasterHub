package com.caglamurat.smartDisasterHub.service.auth;

import com.caglamurat.smartDisasterHub.dto.auth.ForgotPasswordRequest;
import com.caglamurat.smartDisasterHub.dto.auth.LoginRequest;
import com.caglamurat.smartDisasterHub.dto.auth.LoginResponse;
import com.caglamurat.smartDisasterHub.dto.auth.RegisterRequest;
import com.caglamurat.smartDisasterHub.dto.auth.RegisterResponse;
import com.caglamurat.smartDisasterHub.dto.auth.ResetPasswordRequest;
import com.caglamurat.smartDisasterHub.dto.auth.VerifyTokenResponse;

/**
 * Service interface for authentication operations
 */
public interface IAuthService {

    /**
     * Register a new user
     * 
     * @param registerRequest Registration details
     * @return RegisterResponse with JWT token and user details
     * @throws com.caglamurat.smartDisasterHub.exception.BusinessException if registration fails
     */
    RegisterResponse register(RegisterRequest registerRequest);

    /**
     * Authenticate user with email and password
     * 
     * @param loginRequest Login credentials
     * @return LoginResponse with JWT token and user details
     * @throws com.caglamurat.smartDisasterHub.exception.BusinessException if authentication fails
     */
    LoginResponse login(LoginRequest loginRequest);

    /**
     * Verify if a JWT token is valid and extract details
     * 
     * @param token JWT token to verify
     * @return VerifyTokenResponse with validation result and token details
     */
    VerifyTokenResponse verifyToken(String token);

    /**
     * Activate account using email verification token.
     *
     * @param token verification token sent by email
     */
    /**
     * @return user-facing success message (account activation vs email change)
     */
    String activateEmail(String token);

    String forgotPassword(ForgotPasswordRequest request);

    void validatePasswordResetToken(String token);

    String resetPassword(ResetPasswordRequest request);
}

