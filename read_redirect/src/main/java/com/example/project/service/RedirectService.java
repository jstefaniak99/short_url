package com.example.project.service;

import com.example.project.model.ShortUrlEntity;
import com.example.project.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RedirectService {

    private static final Logger logger = LoggerFactory.getLogger(RedirectService.class);
    private final ShortUrlRepository repository;

    public RedirectService(ShortUrlRepository repository) {
        this.repository = repository;
    }

    public String getOriginalUrl(String shortKey) {
        logger.debug("Looking up redirect for key: {}", shortKey);

        ShortUrlEntity entity = repository.findById(shortKey).orElse(null);
        if (entity == null) {
            logger.debug("Short key not found: {}", shortKey);
            return null;
        }

        if (System.currentTimeMillis() > entity.getExpirationTime()) {
            logger.info("Short URL expired, deleting: {}", shortKey);
            repository.delete(entity);
            return null;
        }

        // Aktualizacja czasu ostatniego dostępu
        entity.setLastAccessTime(System.currentTimeMillis());
        repository.save(entity);

        logger.info("Successful redirect for key: {} to URL: {}", shortKey, entity.getOriginalUrl());
        return entity.getOriginalUrl();
    }
}