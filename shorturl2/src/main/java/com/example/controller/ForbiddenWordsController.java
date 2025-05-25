package com.example.controller;

import com.example.service.ForbiddenWordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/admin/forbidden-words")
public class ForbiddenWordsController {

    private final ForbiddenWordService forbiddenWordService;

    public ForbiddenWordsController(ForbiddenWordService forbiddenWordService) {
        this.forbiddenWordService = forbiddenWordService;
    }

    /**
     * Pobranie listy słów zakazanych
     * GET /admin/forbidden-words
     */
    @GetMapping
    public ResponseEntity<Set<String>> getForbiddenWords() {
        return ResponseEntity.ok(forbiddenWordService.getForbiddenWords());
    }

    /**
     * Dodanie nowego słowa zakazanego
     * POST /admin/forbidden-words
     * Body: { "word": "example" }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> addForbiddenWord(@RequestBody Map<String, String> request) {
        String word = request.get("word");
        if (word == null || word.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Word cannot be empty"));
        }

        forbiddenWordService.addForbiddenWord(word);
        return ResponseEntity.ok(Map.of("message", "Word added successfully", "word", word));
    }

    /**
     * Usunięcie słowa zakazanego
     * DELETE /admin/forbidden-words/{word}
     */
    @DeleteMapping("/{word}")
    public ResponseEntity<Map<String, String>> removeForbiddenWord(@PathVariable String word) {
        forbiddenWordService.removeForbiddenWord(word);
        return ResponseEntity.ok(Map.of("message", "Word removed successfully", "word", word));
    }
}