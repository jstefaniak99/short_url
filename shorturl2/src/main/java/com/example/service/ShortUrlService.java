package com.example.service;

import com.example.model.ShortUrlEntity;
import com.example.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ShortUrlService {

    private static final Logger logger = LoggerFactory.getLogger(ShortUrlService.class);

    @Value("${short.url.ttl.seconds:180}")
    private long ttlSeconds;

    private final ShortUrlRepository repository;
    private final SimpleBlockedUrlListener blockedUrlListener;
    private final ForbiddenWordService forbiddenWordService;

    public ShortUrlService(ShortUrlRepository repository,
                           SimpleBlockedUrlListener blockedUrlListener,
                           ForbiddenWordService forbiddenWordService) {
        this.repository = repository;
        this.blockedUrlListener = blockedUrlListener;
        this.forbiddenWordService = forbiddenWordService;
    }

    public String shortenUrl(String originalUrl) {
        logger.info("Attempting to shorten URL: {}", originalUrl);

        if (forbiddenWordService.checkAndAlertIfForbidden(originalUrl)) {
            Optional<String> forbiddenWord = forbiddenWordService.checkForForbiddenWords(originalUrl);
            String message = String.format("URL zawiera zakazane słowo: '%s' - %s",
                    forbiddenWord.orElse("unknown"), originalUrl);
            logger.warn("Rejected URL due to forbidden word: {}", originalUrl);
            throw new IllegalArgumentException(message);
        }

        if (blockedUrlListener.isUrlBlocked(originalUrl)) {
            logger.warn("Rejected blocked URL: {}", originalUrl);
            throw new IllegalArgumentException("URL jest zablokowany przez system bezpieczeństwa: " + originalUrl);
        }

        String shortKey = generateBase62Hash(originalUrl);
        logger.debug("Generated short key: {} for URL: {}", shortKey, originalUrl);

        long expirationTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds);

        ShortUrlEntity entity = new ShortUrlEntity(shortKey, originalUrl, expirationTime);
        repository.save(entity);

        logger.info("Successfully created short URL with key: {}", shortKey);

        // Zwracamy gotowy link
        return "http://localhost:8081/" + shortKey;
    }

    public String getOriginalUrl(String shortKey) {
        logger.debug("Looking up original URL for key: {}", shortKey);

        Optional<ShortUrlEntity> entityOpt = repository.findById(shortKey);
        if (entityOpt.isEmpty()) {
            logger.debug("Short key not found: {}", shortKey);
            return null;
        }

        ShortUrlEntity entity = entityOpt.get();

        // Sprawdza, czy nie wygasł
        if (System.currentTimeMillis() > entity.getExpirationTime()) {
            logger.info("Short URL expired, deleting: {}", shortKey);
            repository.delete(entity);
            return null;
        }

        if (blockedUrlListener.isUrlBlocked(entity.getOriginalUrl())) {
            logger.warn("URL became blocked, deleting short URL: {}", entity.getOriginalUrl());
            repository.delete(entity);
            return null;
        }
        
        if (forbiddenWordService.checkAndAlertIfForbidden(entity.getOriginalUrl())) {
            logger.warn("URL contains forbidden words, deleting short URL: {}", entity.getOriginalUrl());
            repository.delete(entity);
            return null;
        }

        logger.debug("Successfully retrieved original URL: {}", entity.getOriginalUrl());
        return entity.getOriginalUrl();
    }

    private String generateBase62Hash(String originalUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(originalUrl.getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 6; i++) {
                value = (value << 8) | (digest[i] & 0xFF);
            }
            return toBase62(value);
        } catch (Exception e) {
            logger.error("Error generating hash for URL: {}", originalUrl, e);
            throw new RuntimeException("Error generating hash", e);
        }
    }

    private String toBase62(long num) {
        final String base62chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        if (num == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            int remainder = (int) (num % 62);
            sb.append(base62chars.charAt(remainder));
            num /= 62;
        }
        return sb.reverse().toString();
    }
}