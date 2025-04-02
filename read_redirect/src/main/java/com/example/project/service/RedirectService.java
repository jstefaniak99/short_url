package com.example.project.service;

import com.example.project.model.ShortUrlEntity;
import com.example.project.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;

@Service
public class RedirectService {

    private final ShortUrlRepository repository;

    public RedirectService(ShortUrlRepository repository) {
        this.repository = repository;
    }

    public String getOriginalUrl(String shortKey) {
        ShortUrlEntity entity = repository.findById(shortKey).orElse(null);
        if (entity == null) {
            return null;
        }
        if (System.currentTimeMillis() > entity.getExpirationTime()) {
            repository.delete(entity);
            return null;
        }
        return entity.getOriginalUrl();
    }
}
