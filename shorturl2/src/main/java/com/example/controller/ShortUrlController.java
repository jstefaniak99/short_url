package com.example.controller;

import com.example.service.ShortUrlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    public ShortUrlController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    /**
     * Endpoint do skracania URL
     * POST /shorten
     * Body np. { "url": "http://onet.pl" }
     */
    @PostMapping("/shorten")
    public ResponseEntity<Map<String, String>> shortenUrl(@RequestBody Map<String, String> request) {
        String originalUrl = request.get("url");
        if (originalUrl == null || originalUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // Wywołanie serwisu
        String shortUrl = shortUrlService.shortenUrl(originalUrl);
        return ResponseEntity.ok(Map.of("shortUrl", shortUrl));
    }
}
