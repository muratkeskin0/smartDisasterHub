package com.caglamurat.smartDisasterHub.service.user;

import com.caglamurat.smartDisasterHub.dto.user.ManagerCreateDTO;
import com.caglamurat.smartDisasterHub.dto.user.ProfileUpdateDTO;
import com.caglamurat.smartDisasterHub.dto.user.ProfileUpdateResultDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserCreateDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import com.caglamurat.smartDisasterHub.dto.user.UserUpdateDTO;

import java.util.List;
import java.util.Optional;

public interface IUserService {

    UserDTO createUser(UserCreateDTO createDTO);

    UserDTO createManager(ManagerCreateDTO createDTO);

    List<UserDTO> findManagers();

    UserDTO updateUser(Long id, UserUpdateDTO updateDTO);

    UserDTO getCurrentUserProfile();

    ProfileUpdateResultDTO updateCurrentUserProfile(ProfileUpdateDTO updateDTO);

    UserDTO cancelPendingEmailChange();

    Optional<UserDTO> findById(Long id);

    Optional<UserDTO> findByEmail(String email);

    List<UserDTO> findAll();

    List<UserDTO> findVerifiedUsers();

    List<UserDTO> findUsersByRole(Long roleId);

    List<UserDTO> searchUsersByName(String searchTerm);

    void deleteUser(Long id);

    boolean existsByEmail(String email);

    UserDTO updatePassword(Long id, String oldPassword, String newPassword);

    UserDTO verifyEmail(Long id);

    UserDTO changeUserRole(Long userId, Long roleId);

    void resendActivationEmail(Long id);

}

