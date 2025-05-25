package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
// import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimpleBlockedUrlListener {

    private static final Logger logger = LoggerFactory.getLogger(SimpleBlockedUrlListener.class);

    // Prosta lista zablokowanych URL-i
    private final Set<String> blockedUrls = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        logger.info("🎯 SimpleBlockedUrlListener initialized. Waiting for Kafka messages on 'blocked-urls' topic...");
    }

    /**
     * Słucha prostych wiadomości w formacie: "BLOCKED: http://malicious.com"
     */
    @KafkaListener(topics = "blocked-urls", groupId = "url-shortener-group")
    public void handleBlockedUrlMessage(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        logger.info("📨 Received Kafka message from topic '{}', partition {}, offset {}: {}",
                topic, partition, offset, message);

        try {
            // Proste parsowanie - szuka słowa "BLOCKED" i URL-a
            if (message.toUpperCase().contains("BLOCKED")) {
                String url = extractUrl(message);
                if (url != null) {
                    blockedUrls.add(url);
                    logger.warn("⚠️  URL ZABLOKOWANY: {} (Łącznie zablokowanych: {})", url, blockedUrls.size());
                } else {
                    logger.warn("⚠️  Otrzymano wiadomość BLOCKED, ale nie znaleziono URL: {}", message);
                }
            } else {
                logger.info("ℹ️  Otrzymano wiadomość, ale nie zawiera 'BLOCKED': {}", message);
            }

            // Potwierdzenie przetworzenia wiadomości
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            logger.error("❌ Błąd podczas przetwarzania wiadomości Kafka: {}", message, e);
        }
    }

    /**
     * Sprawdza czy URL jest zablokowany
     */
    public boolean isUrlBlocked(String url) {
        boolean blocked = blockedUrls.contains(url);
        if (blocked) {
            logger.warn("🚫 URL jest zablokowany: {}", url);
        }
        return blocked;
    }

    /**
     * Wyciąga URL z wiadomości - bardzo prosty parser
     */
    private String extractUrl(String message) {
        logger.debug("🔍 Extracting URL from message: {}", message);

        // Szuka http:// lub https://
        String[] words = message.split("\\s+");
        for (String word : words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                logger.debug("✅ Found URL in words: {}", word.trim());
                return word.trim();
            }
        }

        // Alternatywnie - regex
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("https?://[^\\s]+");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String url = matcher.group();
            logger.debug("✅ Found URL with regex: {}", url);
            return url;
        }

        logger.debug("❌ No URL found in message");
        return null;
    }

    /**
     * Zwraca wszystkie zablokowane URL-e (do debugowania)
     */
    public Set<String> getBlockedUrls() {
        logger.info("📋 Current blocked URLs count: {}", blockedUrls.size());
        return new java.util.HashSet<>(blockedUrls);
    }

    /**
     * Dodaje URL do listy zablokowanych (do testowania)
     */
    public void addBlockedUrl(String url) {
        blockedUrls.add(url);
        logger.info("➕ Manually added blocked URL: {}", url);
    }

    /**
     * Usuwa URL z listy zablokowanych (do testowania)
     */
    public void removeBlockedUrl(String url) {
        boolean removed = blockedUrls.remove(url);
        if (removed) {
            logger.info("➖ Removed blocked URL: {}", url);
        } else {
            logger.warn("⚠️  Attempted to remove non-existent blocked URL: {}", url);
        }
    }
}