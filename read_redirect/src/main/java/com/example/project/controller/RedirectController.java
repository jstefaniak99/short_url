package com.example.project.controller;

import com.example.project.service.RedirectService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class RedirectController {

    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/{shortKey}")
    public ResponseEntity<?> redirect(@PathVariable String shortKey) {
        String originalUrl = redirectService.getOriginalUrl(shortKey);
        if (originalUrl == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", originalUrl);
        return ResponseEntity.status(302).headers(headers).build();
    }
}
