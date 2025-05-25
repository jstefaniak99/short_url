package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimpleBlockedUrlListener {

    private static final Logger logger = LoggerFactory.getLogger(SimpleBlockedUrlListener.class);

    // Prosta lista zablokowanych URL-i
    private final Set<String> blockedUrls = ConcurrentHashMap.newKeySet();

    /**
     * Słucha prostych wiadomości w formacie: "BLOCKED: http://malicious.com"
     */
    @KafkaListener(topics = "blocked-urls", groupId = "url-shortener-group")
    public void handleBlockedUrlMessage(String message) {
        logger.info("Kafka message: {}", message);

        // Proste parsowanie - szuka słowa "BLOCKED" i URL-a
        if (message.toUpperCase().contains("BLOCKED")) {
            String url = extractUrl(message);
            if (url != null) {
                blockedUrls.add(url);
                logger.warn("⚠️  URL ZABLOKOWANY: {}", url);
            }
        }
    }

    /**
     * Sprawdza czy URL jest zablokowany
     */
    public boolean isUrlBlocked(String url) {
        return blockedUrls.contains(url);
    }

    /**
     * Wyciąga URL z wiadomości - bardzo prosty parser
     */
    private String extractUrl(String message) {
        // Szuka http:// lub https://
        String[] words = message.split("\\s+");
        for (String word : words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                return word.trim();
            }
        }

        // Alternatywnie - regex
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("https?://[^\\s]+");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    /**
     * Zwraca wszystkie zablokowane URL-e (do debugowania)
     */
    public Set<String> getBlockedUrls() {
        return new java.util.HashSet<>(blockedUrls);
    }
}