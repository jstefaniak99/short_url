
package com.example.controller;

import com.example.service.SimpleBlockedUrlListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/admin/blocked-urls")
public class BlockedUrlsController {

    private static final Logger logger = LoggerFactory.getLogger(BlockedUrlsController.class);

    private final SimpleBlockedUrlListener blockedUrlListener;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public BlockedUrlsController(SimpleBlockedUrlListener blockedUrlListener,
                                 KafkaTemplate<String, String> kafkaTemplate) {
        this.blockedUrlListener = blockedUrlListener;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Pobranie listy zablokowanych URL-i
     * GET /admin/blocked-urls
     */
    @GetMapping
    public ResponseEntity<Set<String>> getBlockedUrls() {
        Set<String> blockedUrls = blockedUrlListener.getBlockedUrls();
        logger.info("📋 Returning {} blocked URLs", blockedUrls.size());
        return ResponseEntity.ok(blockedUrls);
    }

    /**
     * Sprawdzenie czy URL jest zablokowany
     * GET /admin/blocked-urls/check?url=http://example.com
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkUrl(@RequestParam String url) {
        boolean blocked = blockedUrlListener.isUrlBlocked(url);
        logger.info("🔍 Checking URL: {} - blocked: {}", url, blocked);

        return ResponseEntity.ok(Map.of(
                "url", url,
                "blocked", blocked,
                "totalBlockedUrls", blockedUrlListener.getBlockedUrls().size()
        ));
    }

    /**
     * Dodanie URL do listy zablokowanych (bezpośrednio)
     * POST /admin/blocked-urls/direct
     * Body: { "url": "http://malicious.com" }
     */
    @PostMapping("/direct")
    public ResponseEntity<Map<String, String>> addBlockedUrlDirect(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL cannot be empty"));
        }

        blockedUrlListener.addBlockedUrl(url);
        logger.info("➕ Directly added blocked URL: {}", url);

        return ResponseEntity.ok(Map.of(
                "message", "URL added to blocked list (direct)",
                "url", url,
                "method", "direct"
        ));
    }

    /**
     * Dodanie URL do listy zablokowanych (przez Kafka)
     * POST /admin/blocked-urls/kafka
     * Body: { "url": "http://malicious.com" }
     */
    @PostMapping("/kafka")
    public ResponseEntity<Map<String, String>> addBlockedUrlViaKafka(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL cannot be empty"));
        }

        try {
            String message = "BLOCKED: " + url;
            kafkaTemplate.send("blocked-urls", message);
            logger.info("📤 Sent blocked URL message to Kafka: {}", message);

            return ResponseEntity.ok(Map.of(
                    "message", "URL sent to Kafka for blocking",
                    "url", url,
                    "method", "kafka",
                    "kafkaMessage", message
            ));
        } catch (Exception e) {
            logger.error("❌ Error sending to Kafka", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to send to Kafka: " + e.getMessage()
            ));
        }
    }

    /**
     * Usunięcie URL z listy zablokowanych
     * DELETE /admin/blocked-urls?url=http://example.com
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> removeBlockedUrl(@RequestParam String url) {
        blockedUrlListener.removeBlockedUrl(url);
        logger.info("➖ Removed blocked URL: {}", url);

        return ResponseEntity.ok(Map.of(
                "message", "URL removed from blocked list",
                "url", url
        ));
    }

    /**
     * Test połączenia z Kafka
     * POST /admin/blocked-urls/test-kafka
     */
    @PostMapping("/test-kafka")
    public ResponseEntity<Map<String, String>> testKafka() {
        try {
            String testMessage = "TEST: Kafka connection test from " + System.currentTimeMillis();
            kafkaTemplate.send("blocked-urls", testMessage);
            logger.info("📤 Sent test message to Kafka: {}", testMessage);

            return ResponseEntity.ok(Map.of(
                    "message", "Test message sent to Kafka successfully",
                    "testMessage", testMessage,
                    "topic", "blocked-urls"
            ));
        } catch (Exception e) {
            logger.error("❌ Kafka test failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Kafka test failed: " + e.getMessage()
            ));
        }
    }
}