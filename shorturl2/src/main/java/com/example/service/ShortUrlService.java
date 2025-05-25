package com.example.service;

import com.example.model.ShortUrlEntity;
import com.example.repository.ShortUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ShortUrlService {

    @Value("${short.url.ttl.seconds:180}")
    private long ttlSeconds;

    private final ShortUrlRepository repository;
    private final SimpleBlockedUrlListener blockedUrlListener;

    public ShortUrlService(ShortUrlRepository repository, SimpleBlockedUrlListener blockedUrlListener) {
        this.repository = repository;
        this.blockedUrlListener = blockedUrlListener;
    }

    public String shortenUrl(String originalUrl) {
        // 🚨 SPRAWDŹ CZY URL JEST ZABLOKOWANY
        if (blockedUrlListener.isUrlBlocked(originalUrl)) {
            throw new RuntimeException("URL jest zablokowany przez system bezpieczeństwa: " + originalUrl);
        }

        // Wygenerowanie klucza
        String shortKey = generateBase62Hash(originalUrl);

        //  Obliczenie czasu wygaśnięcia
        long expirationTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds);

        // Zapis do bazy
        ShortUrlEntity entity = new ShortUrlEntity(shortKey, originalUrl, expirationTime);
        repository.save(entity);

        // Zwracamy gotowy link
        return "http://localhost:8081/" + shortKey;
    }

    public String getOriginalUrl(String shortKey) {
        Optional<ShortUrlEntity> entityOpt = repository.findById(shortKey);
        if (entityOpt.isEmpty()) {
            return null;
        }
        ShortUrlEntity entity = entityOpt.get();

        // Sprawdza, czy nie wygasł
        if (System.currentTimeMillis() > entity.getExpirationTime()) {
            repository.delete(entity); // usuwamy, bo wygasł
            return null;
        }

        // 🚨 SPRAWDŹ CZY URL NIE ZOSTAŁ ZABLOKOWANY W MIĘDZYCZASIE
        if (blockedUrlListener.isUrlBlocked(entity.getOriginalUrl())) {
            repository.delete(entity); // usuń zablokowany URL
            return null;
        }

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