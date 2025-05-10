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

    private static final Logger logger = LoggerFactory.getLogger(CleanupService.class);

    private final ShortUrlRepository repository;

    /* ---------- KONFIGURACJA W MINUTACH -------------------------- */

    @Value("${cleanup.max-age:3}")
    private long maxAgeMinutes;          // nowość

    @Value("${cleanup.inactive:3}")
    private long inactiveMinutes;        // nowość

    @Value("${cleanup.strategy:CREATION_TIME}")
    private CleanupStrategy cleanupStrategy;

    public CleanupService(ShortUrlRepository repository) {
        this.repository = repository;
    }

    public enum CleanupStrategy {
        CREATION_TIME,
        LAST_ACCESS_TIME
    }

    /** Uruchamiane z scheduler’a co minutę. */
    public int cleanupOldUrls() {
        long now = System.currentTimeMillis();
        List<ShortUrlEntity> toDelete;

        if (cleanupStrategy == CleanupStrategy.CREATION_TIME) {
            long cutoff = now - TimeUnit.MINUTES.toMillis(maxAgeMinutes);
            logger.info("Cleaning URLs created before: {}", cutoff);
            toDelete = repository.findAllWithCreationTimeBefore(cutoff);
        } else {
            long cutoff = now - TimeUnit.MINUTES.toMillis(inactiveMinutes);
            logger.info("Cleaning URLs not accessed since: {}", cutoff);
            toDelete = repository.findAllWithLastAccessTimeBefore(cutoff);
        }

        int count = toDelete.size();
        if (count > 0) {
            logger.info("Deleting {} expired short URLs", count);
            repository.deleteAll(toDelete);
        } else {
            logger.info("No expired short URLs found");
        }
        return count;
    }
}