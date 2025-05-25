package com.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ForbiddenWordService {

    private static final Logger logger = LoggerFactory.getLogger(ForbiddenWordService.class);

    @Value("${forbidden.words.kafka.topic:forbidden-words-topic}")
    private String kafkaTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Set<String> forbiddenWords;

    public ForbiddenWordService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.forbiddenWords = initializeForbiddenWords();
    }

    /**
     * Inicjalizuje listę słów zakazanych.
     * Można to przenieść do konfiguracji external lub bazy danych.
     */
    private Set<String> initializeForbiddenWords() {
        Set<String> words = new HashSet<>();
        // Przykładowe słowa zakazane
        words.add("spam");
        words.add("phishing");
        words.add("malware");
        words.add("onet");

        return words;
    }

    /**
     * Sprawdza czy URL zawiera słowa zakazane
     * @param url URL do sprawdzenia
     * @return Optional z pierwszym znalezionym słowem zakazanym lub empty
     */
    public Optional<String> checkForForbiddenWords(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        String urlLowerCase = url.toLowerCase();

        return forbiddenWords.stream()
                .filter(forbiddenWord -> urlLowerCase.contains(forbiddenWord.toLowerCase()))
                .findFirst();
    }

    /**
     * Wysyła wiadomość na Kafkę o znalezieniu słowa zakazanego
     * @param url Oryginalny URL
     * @param forbiddenWord Znalezione słowo zakazane
     */
    public void sendForbiddenWordAlert(String url, String forbiddenWord) {
        try {
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            alertData.put("url", url);
            alertData.put("forbiddenWord", forbiddenWord);
            alertData.put("eventType", "FORBIDDEN_WORD_DETECTED");

            String jsonMessage = objectMapper.writeValueAsString(alertData);

            kafkaTemplate.send(kafkaTopic, jsonMessage);

            logger.warn("Forbidden word '{}' detected in URL: {}", forbiddenWord, url);

        } catch (Exception e) {
            logger.error("Error sending forbidden word alert to Kafka", e);
        }
    }

    /**
     * Dodaje nowe słowo zakazane do listy
     * @param word Słowo do dodania
     */
    public void addForbiddenWord(String word) {
        if (word != null && !word.isBlank()) {
            forbiddenWords.add(word.toLowerCase().trim());
        }
    }

    /**
     * Usuwa słowo zakazane z listy
     * @param word Słowo do usunięcia
     */
    public void removeForbiddenWord(String word) {
        if (word != null) {
            forbiddenWords.remove(word.toLowerCase().trim());
        }
    }

    /**
     * Zwraca aktualną listę słów zakazanych
     * @return Set słów zakazanych
     */
    public Set<String> getForbiddenWords() {
        return new HashSet<>(forbiddenWords);
    }
}