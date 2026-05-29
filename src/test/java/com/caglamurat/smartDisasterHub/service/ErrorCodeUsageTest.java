package com.caglamurat.smartDisasterHub.service;

import com.caglamurat.smartDisasterHub.dto.user.UserCreateDTO;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.exception.ResourceNotFoundException;
import com.caglamurat.smartDisasterHub.repository.IUserRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRoleRepository;
import com.caglamurat.smartDisasterHub.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test class to verify ErrorCode usage in services
 */
@SpringBootTest
class ErrorCodeUsageTest {

    @Autowired
    private UserService userService;

    @MockBean
    private IUserRepository userRepository;

    @MockBean
    private IUserRoleRepository roleRepository;

    @Test
    void testEmailAlreadyExists_ThrowsBusinessException() {
        // Given
        UserCreateDTO createDTO = UserCreateDTO.builder()
                .email("existing@email.com")
                .firstName("Test")
                .lastName("User")
                .password("123456")
                .roleId(1L)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.createUser(createDTO)
        );

        // Verify ErrorCode
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        assertEquals("EMAIL_ALREADY_EXISTS", exception.getErrorCode().name());
        assertEquals("USER_003", exception.getErrorCode().getCode());
        assertEquals("Email already exists", exception.getMessage());
        assertTrue(exception.getDetails().contains("existing@email.com"));
    }

    @Test
    void testRoleNotFound_ThrowsResourceNotFoundException() {
        // Given
        UserCreateDTO createDTO = UserCreateDTO.builder()
                .email("test@email.com")
                .firstName("Test")
                .lastName("User")
                .password("123456")
                .roleId(999L)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.createUser(createDTO)
        );

        // Verify ErrorCode
        assertEquals(ErrorCode.ROLE_NOT_FOUND, exception.getErrorCode());
        assertEquals("ROLE_001", exception.getErrorCode().getCode());
        assertEquals("Role not found", exception.getMessage());
        assertTrue(exception.getDetails().contains("999"));
    }

    @Test
    void testErrorCode_Properties() {
        // Test ErrorCode.EMAIL_ALREADY_EXISTS
        ErrorCode errorCode = ErrorCode.EMAIL_ALREADY_EXISTS;

        assertEquals("USER_003", errorCode.getCode());
        assertEquals("Email already exists", errorCode.getMessage());
        assertEquals(409, errorCode.getHttpStatus().value()); // CONFLICT
        assertFalse(errorCode.isCritical());
        assertFalse(errorCode.shouldLog()); // LOW severity
        assertEquals("[USER_003] Email already exists", errorCode.getFormattedMessage());
    }

    @Test
    void testErrorCode_Severity() {
        // LOW severity - should not log
        assertFalse(ErrorCode.VALIDATION_ERROR.shouldLog());
        assertFalse(ErrorCode.EMAIL_ALREADY_EXISTS.shouldLog());

        // MEDIUM severity - should log
        assertTrue(ErrorCode.USER_NOT_FOUND.shouldLog());
        assertTrue(ErrorCode.EMAIL_NOT_VERIFIED.shouldLog());

        // CRITICAL severity - should log and is critical
        assertTrue(ErrorCode.INTERNAL_SERVER_ERROR.shouldLog());
        assertTrue(ErrorCode.INTERNAL_SERVER_ERROR.isCritical());
        assertTrue(ErrorCode.DATABASE_ERROR.isCritical());
    }
}


