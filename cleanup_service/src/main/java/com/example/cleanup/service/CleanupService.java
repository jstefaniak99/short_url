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

    @Value("${cleanup.max-age-days:365}")
    private long maxAgeDays;

    @Value("${cleanup.inactive-days:90}")
    private long inactiveDays;

    @Value("${cleanup.strategy:CREATION_TIME}")
    private CleanupStrategy cleanupStrategy;

    public CleanupService(ShortUrlRepository repository) {
        this.repository = repository;
    }

    public enum CleanupStrategy {
        CREATION_TIME,
        LAST_ACCESS_TIME
    }

    public int cleanupOldUrls() {
        long now = System.currentTimeMillis();
        List<ShortUrlEntity> toDelete;

        if (cleanupStrategy == CleanupStrategy.CREATION_TIME) {
            long cutoffTime = now - TimeUnit.DAYS.toMillis(maxAgeDays);
            logger.info("Cleaning up URLs created before: {}", cutoffTime);
            toDelete = repository.findAllWithCreationTimeBefore(cutoffTime);
        } else {
            long cutoffTime = now - TimeUnit.DAYS.toMillis(inactiveDays);
            logger.info("Cleaning up URLs not accessed since: {}", cutoffTime);
            toDelete = repository.findAllWithLastAccessTimeBefore(cutoffTime);
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