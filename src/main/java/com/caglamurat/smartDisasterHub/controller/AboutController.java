package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.about.AboutDTO;
import com.caglamurat.smartDisasterHub.service.about.IAboutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/about")
@RequiredArgsConstructor
@Slf4j
public class AboutController {

    private final IAboutService aboutService;

    @GetMapping
    public ResponseEntity<ApiResponse<AboutDTO>> getAbout() {
        log.info("Getting About content");
        AboutDTO about = aboutService.getAbout();
        return ResponseEntity.ok(ApiResponse.success("About content retrieved successfully", about));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AboutDTO>> updateAbout(@RequestBody AboutDTO aboutDTO) {
        log.info("Updating About content");
        AboutDTO updated = aboutService.updateAbout(aboutDTO);
        return ResponseEntity.ok(ApiResponse.success("About content updated successfully", updated));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AboutDTO>> createAbout(@RequestBody AboutDTO aboutDTO) {
        log.info("Creating About content");
        AboutDTO created = aboutService.createAbout(aboutDTO);
        return ResponseEntity.ok(ApiResponse.success("About content created successfully", created));
    }
}





