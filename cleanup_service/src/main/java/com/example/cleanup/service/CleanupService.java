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

    @Value("${cleanup.max-age-minutes:3}")
    private long maxAgeMinutes;

    @Value("${cleanup.inactive-minutes:3}")
    private long inactiveMinutes;

    @Value("${cleanup.strategy:CREATION_TIME}")
    private CleanupStrategy strategy;

    public CleanupService(ShortUrlRepository repo) {
        this.repo = repo;
    }

    public enum CleanupStrategy { CREATION_TIME, LAST_ACCESS_TIME }

    public int cleanupOldUrls() {
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

        /* ----------- LOGUJEMY *po* realnym kasowaniu ---------------- */
        int count = toDelete.size();
        if (count > 0) {
            repo.deleteAll(toDelete);
            log.info("Deleted {} expired short URLs", count);
        } else {
            log.info("No expired short URLs found");
        }
        return count;
    }
}