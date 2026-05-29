package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.service.media.ExternalMediaProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaProxyController {

    private final ExternalMediaProxyService externalMediaProxyService;

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        ExternalMediaProxyService.ProxiedMedia media = externalMediaProxyService.fetch(url);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(media.contentType())
                .body(media.bytes());
    }
}
