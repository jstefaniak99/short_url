package com.example.cleanup.service;

import com.example.cleanup.model.ShortUrlEntity;
import com.example.cleanup.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CleanupService {

    private final ShortUrlRepository repo;
    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    @Value("${cleanup.max-age:3}")
    private long maxAgeMinutes;

    @Value("${cleanup.inactive:3}")
    private long inactiveMinutes;

    @Value("${cleanup.strategy:CREATION_TIME}")
    private CleanupStrategy strategy;

    public CleanupService(ShortUrlRepository repo) {
        this.repo = repo;
    }

    public enum CleanupStrategy {
        CREATION_TIME,
        LAST_ACCESS_TIME
    }

    public int cleanupOldUrls() {
        // 1. liczba rekordów PRZED
        long totalBefore = repo.count();

        long now = System.currentTimeMillis();
        List<ShortUrlEntity> toDelete;

        if (strategy == CleanupStrategy.CREATION_TIME) {
            long cutoff = now - TimeUnit.MINUTES.toMillis(maxAgeMinutes);
            log.info("Cleaning URLs created before {}", cutoff);
            toDelete = repo.findAllWithCreationTimeBefore(cutoff);
        } else {
            long cutoff = now - TimeUnit.MINUTES.toMillis(inactiveMinutes);
            log.info("Cleaning URLs not accessed since {}", cutoff);
            toDelete = repo.findAllWithLastAccessTimeBefore(cutoff);
        }

        // 2. właściwe kasowanie
        if (!toDelete.isEmpty()) {
            repo.deleteAll(toDelete);
        }

        // 3. liczba rekordów PO
        long totalAfter = repo.count();
        long removed = totalBefore - totalAfter;

        log.info("DB shrank by {} rows in last cleanup ({} → {})", removed, totalBefore, totalAfter);

        return (int) removed;
    }
}