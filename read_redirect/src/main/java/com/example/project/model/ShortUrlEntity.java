package com.example.project.model;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("short_url_entity")
public class ShortUrlEntity {

    @PrimaryKey
    private String shortKey;

    private String originalUrl;
    private long expirationTime;

    public ShortUrlEntity() {}

    public ShortUrlEntity(String shortKey, String originalUrl, long expirationTime) {
        this.shortKey = shortKey;
        this.originalUrl = originalUrl;
        this.expirationTime = expirationTime;
    }

    public String getShortKey() {
        return shortKey;
    }

    public void setShortKey(String shortKey) {
        this.shortKey = shortKey;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }
}
