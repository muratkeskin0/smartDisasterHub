package com.caglamurat.smartDisasterHub.service.auth;

import com.caglamurat.smartDisasterHub.config.JwtUtil;
import com.caglamurat.smartDisasterHub.domain.EmailVerificationToken;
import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.domain.UserRole;
import com.caglamurat.smartDisasterHub.dto.auth.ForgotPasswordRequest;
import com.caglamurat.smartDisasterHub.dto.auth.LoginRequest;
import com.caglamurat.smartDisasterHub.dto.auth.LoginResponse;
import com.caglamurat.smartDisasterHub.dto.auth.RegisterRequest;
import com.caglamurat.smartDisasterHub.dto.auth.RegisterResponse;
import com.caglamurat.smartDisasterHub.dto.auth.ResetPasswordRequest;
import com.caglamurat.smartDisasterHub.dto.auth.VerifyTokenResponse;
import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import com.caglamurat.smartDisasterHub.enums.EmailVerificationPurpose;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.mapper.user.UserMapper;
import com.caglamurat.smartDisasterHub.repository.IEmailVerificationTokenRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRoleRepository;
import com.caglamurat.smartDisasterHub.service.mail.IEmailService;
import com.caglamurat.smartDisasterHub.service.mail.IEmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Service implementation for authentication operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements IAuthService {

    private final IUserRepository userRepository;
    private final IUserRoleRepository userRoleRepository;
    private final IEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final IEmailService emailService;
    private final IEmailTemplateService emailTemplateService;

    @Value("${app.web.url}")
    private String webUrl;

    /**
     * Register a new user
     * Creates user with default USER role and sends activation email
     */
    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        log.info("Registration attempt for email: {}", registerRequest.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            log.warn("Registration failed: Email already exists: {}", registerRequest.getEmail());
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email already exists"
            );
        }

        // Find BASIC role (default role for registration)
        UserRole userRole = userRoleRepository.findByName(UserRoleType.BASIC)
                .orElseThrow(() -> {
                    log.error("BASIC role not found in database");
                    return new BusinessException(
                            ErrorCode.ROLE_NOT_FOUND,
                            "Default BASIC role not found. Please contact administrator."
                    );
                });

        // Encode password
        String encodedPassword = passwordEncoder.encode(registerRequest.getPassword());

        // Convert RegisterRequest to User entity using mapper
        User newUser = userMapper.toEntity(registerRequest, userRole, encodedPassword);

        // Save user to database
        User savedUser = userRepository.save(newUser);
        log.info("User registered successfully: {} with ID: {}", savedUser.getEmail(), savedUser.getId());

        if (!emailService.isConfigured()) {
            log.warn("SMTP not configured — auto-verifying user {} for sign-in without activation email", savedUser.getEmail());
            savedUser.setIsEmailVerified(true);
            savedUser = userRepository.save(savedUser);
            UserDTO userDTO = userMapper.toDTO(savedUser);
            return new RegisterResponse(true, savedUser.getEmail(), userDTO);
        }

        String verificationToken = UUID.randomUUID().toString();
        EmailVerificationToken emailToken = EmailVerificationToken.builder()
                .user(savedUser)
                .token(verificationToken)
                .purpose(EmailVerificationPurpose.ACCOUNT_ACTIVATION)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.save(emailToken);

        String activationLink = webUrl + "/activate-email?token=" + verificationToken;
        String fullName = savedUser.getFirstName() + " " + savedUser.getLastName();
        String emailBody = emailTemplateService.buildActivationEmail(fullName, activationLink);
        emailService.sendHtmlEmail(savedUser.getEmail(), "Activate your Smart Disaster Hub account", emailBody);

        // Convert user to DTO
        UserDTO userDTO = userMapper.toDTO(savedUser);

        return new RegisterResponse(true, savedUser.getEmail(), userDTO);
    }

    /**
     * Authenticate user with email and password
     * Validates credentials, checks email verification, and generates JWT token
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest loginRequest) {
        log.info("Login attempt for email: {}", loginRequest.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found with email: {}", loginRequest.getEmail());
                    return new BusinessException(
                            ErrorCode.AUTHENTICATION_FAILED,
                            "Invalid email or password"
                    );
                });

        // Verify password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.warn("Login failed: Invalid password for email: {}", loginRequest.getEmail());
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_FAILED,
                    "Invalid email or password"
            );
        }

        // Check if email is verified
        if (!user.getIsEmailVerified()) {
            log.warn("Login failed: Email not verified for: {}", loginRequest.getEmail());
            throw new BusinessException(
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    "Please verify your email before logging in"
            );
        }

        // Generate JWT token
        // Note: Using email as username (first parameter is the "subject" of JWT)
        String token = jwtUtil.generateToken(
                user.getEmail(),                   // username (email)
                user.getId(),                      // userId (stored in claims)
                user.getRole().getName().name()    // role (stored in claims) - convert enum to String
        );

        // Convert user to DTO
        UserDTO userDTO = userMapper.toDTO(user);

        log.info("User logged in successfully: {} with role: {}", user.getEmail(), user.getRole().getName().name());
        
        return new LoginResponse(token, userDTO);
    }

    /**
     * Verify if a JWT token is valid and extract details
     */
    @Override
    public VerifyTokenResponse verifyToken(String token) {
        try {
            String email = jwtUtil.extractUsername(token);
            boolean isValid = jwtUtil.validateToken(token, email);
            
            if (isValid) {
                String role = jwtUtil.extractRole(token);
                log.debug("Token is valid for user: {} with role: {}", email, role);
                
                return VerifyTokenResponse.builder()
                        .valid(true)
                        .email(email)
                        .role(role)
                        .build();
            } else {
                log.debug("Token is invalid");
                return VerifyTokenResponse.builder()
                        .valid(false)
                        .build();
            }
        } catch (Exception e) {
            log.error("Token verification failed: {}", e.getMessage());
            return VerifyTokenResponse.builder()
                    .valid(false)
                    .build();
        }
    }

    @Override
    @Transactional
    public String activateEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                        "Activation link is invalid"
                ));

        if (verificationToken.isUsed()) {
            throw new BusinessException(
                    ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                    "Activation link has already been used"
            );
        }

        if (verificationToken.isExpired()) {
            throw new BusinessException(
                    ErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED,
                    "Activation link has expired"
            );
        }

        User user = verificationToken.getUser();
        EmailVerificationPurpose purpose = verificationToken.getPurpose() != null
                ? verificationToken.getPurpose()
                : EmailVerificationPurpose.ACCOUNT_ACTIVATION;

        String successMessage;
        if (purpose == EmailVerificationPurpose.EMAIL_CHANGE) {
            String pendingEmail = verificationToken.getPendingEmail();
            if (pendingEmail == null || pendingEmail.isBlank()) {
                throw new BusinessException(
                        ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                        "Email change link is invalid"
                );
            }
            String normalized = pendingEmail.trim().toLowerCase();
            userRepository.findByEmail(normalized).ifPresent(other -> {
                if (!other.getId().equals(user.getId())) {
                    throw new BusinessException(
                            ErrorCode.EMAIL_ALREADY_EXISTS,
                            "This email is already registered to another account"
                    );
                }
            });
            user.setEmail(normalized);
            user.setIsEmailVerified(true);
            userRepository.save(user);
            emailVerificationTokenRepository.deleteByUserAndPurpose(user, EmailVerificationPurpose.EMAIL_CHANGE);
            log.info("Email changed for user id {} to {}", user.getId(), normalized);
            successMessage = "Your email address has been updated. Please sign in with your new email.";
        } else {
            user.setIsEmailVerified(true);
            userRepository.save(user);
            log.info("Email activated for user {}", user.getEmail());
            successMessage = "Email activated successfully. You can now sign in.";
        }

        verificationToken.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(verificationToken);
        return successMessage;
    }

    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        log.info("Password reset requested for email: {}", email);

        if (!emailService.isConfigured()) {
            throw new BusinessException(
                    ErrorCode.EMAIL_DELIVERY_FAILED,
                    "Email delivery is not configured. Please contact support."
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "No account found with this email address"
                ));

        emailVerificationTokenRepository.deleteByUserAndPurpose(user, EmailVerificationPurpose.PASSWORD_RESET);

        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken resetToken = EmailVerificationToken.builder()
                .user(user)
                .token(tokenValue)
                .purpose(EmailVerificationPurpose.PASSWORD_RESET)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.save(resetToken);

        String resetLink = webUrl + "/reset-password?token=" + tokenValue;
        String fullName = user.getFirstName() + " " + user.getLastName();
        String emailBody = emailTemplateService.buildPasswordResetEmail(fullName, resetLink);
        emailService.sendHtmlEmail(user.getEmail(), "Reset your Smart Disaster Hub password", emailBody);
        log.info("Password reset email sent to {}", user.getEmail());

        return "Password reset link sent to your email.";
    }

    @Override
    @Transactional(readOnly = true)
    public void validatePasswordResetToken(String token) {
        findValidPasswordResetToken(token);
    }

    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Password and confirm password do not match"
            );
        }

        EmailVerificationToken resetToken = findValidPasswordResetToken(request.getToken());
        User user = resetToken.getUser();

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(resetToken);
        emailVerificationTokenRepository.deleteByUserAndPurpose(user, EmailVerificationPurpose.PASSWORD_RESET);

        log.info("Password reset completed for user {}", user.getEmail());
        return "Your password has been updated. You can now sign in.";
    }

    private EmailVerificationToken findValidPasswordResetToken(String token) {
        EmailVerificationToken resetToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                        "Password reset link is invalid"
                ));

        if (resetToken.getPurpose() != EmailVerificationPurpose.PASSWORD_RESET) {
            throw new BusinessException(
                    ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                    "Password reset link is invalid"
            );
        }

        if (resetToken.isUsed()) {
            throw new BusinessException(
                    ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,
                    "Password reset link has already been used"
            );
        }

        if (resetToken.isExpired()) {
            throw new BusinessException(
                    ErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED,
                    "Password reset link has expired"
            );
        }

        return resetToken;
    }
}

