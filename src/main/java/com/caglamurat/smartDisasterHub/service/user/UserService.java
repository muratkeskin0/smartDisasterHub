package com.caglamurat.smartDisasterHub.service.user;

import com.caglamurat.smartDisasterHub.domain.EmailVerificationToken;
import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.domain.UserRole;
import com.caglamurat.smartDisasterHub.dto.user.ManagerCreateDTO;
import com.caglamurat.smartDisasterHub.dto.user.ProfileUpdateDTO;
import com.caglamurat.smartDisasterHub.dto.user.ProfileUpdateResultDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserCreateDTO;
import com.caglamurat.smartDisasterHub.enums.EmailVerificationPurpose;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserUpdateDTO;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.exception.ResourceNotFoundException;
import com.caglamurat.smartDisasterHub.mapper.user.UserMapper;
import com.caglamurat.smartDisasterHub.repository.IEmailVerificationTokenRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRoleRepository;
import com.caglamurat.smartDisasterHub.security.SecurityUserContext;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService implements IUserService {

    private final IUserRepository IUserRepository;
    private final IUserRoleRepository IUserRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUserContext securityUserContext;
    private final IEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final IEmailService emailService;
    private final IEmailTemplateService emailTemplateService;

    @Value("${app.web.url}")
    private String webUrl;

    @Override
    public UserDTO createUser(UserCreateDTO createDTO) {
        log.info("Creating new user with email: {}", createDTO.getEmail());

        if (existsByEmail(createDTO.getEmail())) {
            throw new BusinessException(
                ErrorCode.EMAIL_ALREADY_EXISTS, 
                "Email already exists: " + createDTO.getEmail()
            );
        }

        UserRole role = IUserRoleRepository.findById(createDTO.getRoleId())
                .orElseThrow(() -> ResourceNotFoundException.roleNotFound(createDTO.getRoleId()));

        if (role.getName() == UserRoleType.ADMIN) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "Cannot create ADMIN users via API"
            );
        }

        // Şifre hash'leme
        String hashedPassword = passwordEncoder.encode(createDTO.getPassword());
        log.debug("Password hashed successfully for user: {}", createDTO.getEmail());

        User user = userMapper.toEntity(createDTO, role);
        user.setPassword(hashedPassword);
        User savedUser = IUserRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());

        return userMapper.toDTO(savedUser);
    }

    @Override
    public UserDTO createManager(ManagerCreateDTO createDTO) {
        UserRole managerRole = IUserRoleRepository.findByName(UserRoleType.MANAGER)
                .orElseThrow(() -> ResourceNotFoundException.roleNotFoundByName("MANAGER"));

        UserCreateDTO userCreate = UserCreateDTO.builder()
                .firstName(createDTO.getFirstName())
                .lastName(createDTO.getLastName())
                .email(createDTO.getEmail())
                .password(createDTO.getPassword())
                .roleId(managerRole.getId())
                .build();
        UserDTO created = createUser(userCreate);
        User user = IUserRepository.findById(created.getId())
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(created.getId()));
        user.setIsEmailVerified(true);
        IUserRepository.save(user);
        created.setIsEmailVerified(true);
        log.info("Manager account created and pre-verified: {}", created.getEmail());
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> findManagers() {
        UserRole managerRole = IUserRoleRepository.findByName(UserRoleType.MANAGER)
                .orElseThrow(() -> ResourceNotFoundException.roleNotFoundByName("MANAGER"));
        return userMapper.toDTOList(IUserRepository.findByRoleId(managerRole.getId()));
    }

    @Override
    public UserDTO updateUser(Long id, UserUpdateDTO updateDTO) {
        log.info("Updating user with ID: {}", id);

        User existingUser = IUserRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(id));

        if (updateDTO.getEmail() != null &&
            !existingUser.getEmail().equals(updateDTO.getEmail()) && 
            existsByEmail(updateDTO.getEmail())) {
            throw new BusinessException(
                ErrorCode.EMAIL_ALREADY_EXISTS, 
                "Email already exists: " + updateDTO.getEmail()
            );
        }

        UserRole role = null;
        if (updateDTO.getRoleId() != null) {
            role = IUserRoleRepository.findById(updateDTO.getRoleId())
                    .orElseThrow(() -> ResourceNotFoundException.roleNotFound(updateDTO.getRoleId()));
        }

        // Şifre değişikliği varsa hash'le
        if (updateDTO.getPassword() != null && !updateDTO.getPassword().trim().isEmpty()) {
            String hashedPassword = passwordEncoder.encode(updateDTO.getPassword());
            updateDTO.setPassword(hashedPassword);
            log.debug("Password updated and hashed for user ID: {}", id);
        }

        userMapper.updateEntityFromDTO(updateDTO, existingUser, role);
        User updatedUser = IUserRepository.save(existingUser);
        log.info("User updated successfully: {}", updatedUser.getId());

        return userMapper.toDTO(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getCurrentUserProfile() {
        User user = securityUserContext.requireCurrentUser();
        return enrichWithPendingEmailChange(userMapper.toDTO(user), user);
    }

    @Override
    public ProfileUpdateResultDTO updateCurrentUserProfile(ProfileUpdateDTO updateDTO) {
        User current = securityUserContext.requireCurrentUser();
        String newEmail = normalizeEmail(updateDTO.getEmail());
        String currentEmail = normalizeEmail(current.getEmail());

        UserUpdateDTO patch = UserUpdateDTO.builder()
                .firstName(updateDTO.getFirstName().trim())
                .lastName(updateDTO.getLastName().trim())
                .build();
        UserDTO updated = updateUser(current.getId(), patch);

        if (newEmail.equalsIgnoreCase(currentEmail)) {
            emailVerificationTokenRepository.deleteByUserAndPurpose(current, EmailVerificationPurpose.EMAIL_CHANGE);
            updated = enrichWithPendingEmailChange(updated, current);
            return ProfileUpdateResultDTO.builder()
                    .user(updated)
                    .message("Profile updated successfully")
                    .emailChangePending(false)
                    .build();
        }

        assertEmailAvailableForChange(current, newEmail);
        requestEmailChange(current, newEmail);
        updated = enrichWithPendingEmailChange(updated, current);

        return ProfileUpdateResultDTO.builder()
                .user(updated)
                .message("Profile updated. Confirm your new email address using the link sent to " + newEmail
                        + ". Your current email remains active until then.")
                .emailChangePending(true)
                .activationSentTo(newEmail)
                .build();
    }

    private void assertEmailAvailableForChange(User user, String newEmail) {
        Optional<User> existing = IUserRepository.findByEmail(newEmail);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    "This email is already registered to another account"
            );
        }
    }

    private void requestEmailChange(User user, String newEmail) {
        emailVerificationTokenRepository.deleteByUserAndPurpose(user, EmailVerificationPurpose.EMAIL_CHANGE);

        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .token(tokenValue)
                .purpose(EmailVerificationPurpose.EMAIL_CHANGE)
                .pendingEmail(newEmail)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.save(token);

        String confirmationLink = webUrl + "/activate-email?token=" + tokenValue;
        String fullName = user.getFirstName() + " " + user.getLastName();
        String body = emailTemplateService.buildEmailChangeEmail(
                fullName, user.getEmail(), newEmail, confirmationLink);
        emailService.sendHtmlEmail(newEmail, "Confirm your new Smart Disaster Hub email", body);
        log.info("Email change confirmation sent to {} for user id {}", newEmail, user.getId());
    }

    private UserDTO enrichWithPendingEmailChange(UserDTO dto, User user) {
        emailVerificationTokenRepository
                .findFirstByUserAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        user, EmailVerificationPurpose.EMAIL_CHANGE, Instant.now())
                .ifPresent(token -> {
                    dto.setPendingEmail(token.getPendingEmail());
                    dto.setEmailChangePending(true);
                });
        if (dto.getEmailChangePending() == null) {
            dto.setEmailChangePending(false);
        }
        return dto;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    @Override
    public UserDTO cancelPendingEmailChange() {
        User current = securityUserContext.requireCurrentUser();
        emailVerificationTokenRepository.deleteByUserAndPurpose(current, EmailVerificationPurpose.EMAIL_CHANGE);
        User refreshed = IUserRepository.findById(current.getId())
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(current.getId()));
        log.info("Cancelled pending email change for user id {}", current.getId());
        return enrichWithPendingEmailChange(userMapper.toDTO(refreshed), refreshed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findById(Long id) {
        log.debug("Finding user by ID: {}", id);
        return IUserRepository.findById(id)
                .map(userMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return IUserRepository.findByEmail(email)
                .map(userMapper::toDTO);
    }


    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> findAll() {
        log.debug("Finding all users");
        return userMapper.toDTOList(IUserRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> findVerifiedUsers() {
        log.debug("Finding verified users");
        return userMapper.toDTOList(IUserRepository.findByIsEmailVerifiedTrue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> findUsersByRole(Long roleId) {
        log.debug("Finding users by role ID: {}", roleId);
        return userMapper.toDTOList(IUserRepository.findByRoleId(roleId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> searchUsersByName(String searchTerm) {
        log.debug("Searching users by name: {}", searchTerm);
        return userMapper.toDTOList(IUserRepository.searchByName(searchTerm));
    }

    @Override
    public void deleteUser(Long id) {
        log.warn("Deleting user with ID: {}", id);

        User user = IUserRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(id));

        IUserRepository.delete(user);
        log.info("User hard deleted successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return IUserRepository.existsByEmail(email);
    }

    @Override
    public UserDTO updatePassword(Long id, String oldPassword, String newPassword) {
        log.info("Updating password for user ID: {}", id);

        User user = IUserRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(id));

        // Eski şifre doğrulama
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Invalid old password attempt for user ID: {}", id);
            throw new BusinessException(
                ErrorCode.INVALID_CREDENTIALS, 
                "Old password is incorrect"
            );
        }

        // Yeni şifre hash'leme
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(hashedPassword);

        User updatedUser = IUserRepository.save(user);
        log.info("Password updated successfully for user: {}", id);

        return userMapper.toDTO(updatedUser);
    }

    @Override
    public UserDTO verifyEmail(Long id) {
        log.info("Verifying email for user ID: {}", id);

        User user = IUserRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(id));

        user.setIsEmailVerified(true);
        User updatedUser = IUserRepository.save(user);

        log.info("Email verified successfully for user: {}", id);
        return userMapper.toDTO(updatedUser);
    }

    @Override
    public UserDTO changeUserRole(Long userId, Long roleId) {
        log.info("Changing role for user ID: {} to role ID: {}", userId, roleId);

        User user = IUserRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(userId));

        UserRole role = IUserRoleRepository.findById(roleId)
                .orElseThrow(() -> ResourceNotFoundException.roleNotFound(roleId));

        if (role.getName() == UserRoleType.ADMIN) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "Cannot assign ADMIN role via API"
            );
        }

        user.setRole(role);
        User updatedUser = IUserRepository.save(user);

        log.info("User role changed successfully for user: {}", userId);
        return userMapper.toDTO(updatedUser);
    }
}

