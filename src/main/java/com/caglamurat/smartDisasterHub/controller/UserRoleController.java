package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.user.UserRoleDTO;
import com.caglamurat.smartDisasterHub.exception.ResourceNotFoundException;
import com.caglamurat.smartDisasterHub.mapper.user.UserRoleMapper;
import com.caglamurat.smartDisasterHub.service.user.IUserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class UserRoleController {

    private final IUserRoleService _userRoleService;
    private final UserRoleMapper _userRoleMapper;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserRoleDTO>> getRoleById(@PathVariable Long id) {
        log.debug("REST request to get role by ID: {}", id);
        UserRoleDTO role = _userRoleService.findById(id)
                .map(_userRoleMapper::toDTO)
                .orElseThrow(() -> ResourceNotFoundException.roleNotFound(id));
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<ApiResponse<UserRoleDTO>> getRoleByName(@PathVariable String name) {
        log.debug("REST request to get role by name: {}", name);
        UserRoleDTO role = _userRoleService.findByName(name)
                .map(_userRoleMapper::toDTO)
                .orElseThrow(() -> ResourceNotFoundException.roleNotFoundByName(name));
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserRoleDTO>>> getAllRoles() {
        log.debug("REST request to get all roles");
        List<UserRoleDTO> roles = _userRoleMapper.toDTOList(_userRoleService.findAll());
        return ResponseEntity.ok(ApiResponse.success(roles));
    }
}

