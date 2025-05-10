package com.example.model;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("short_url_entity")
public class ShortUrlEntity {

    @PrimaryKey("short_key")
    private String shortKey;

    @Column("original_url")
    private String originalUrl;

    @Column("expiration_time")
    private long expirationTime;

    @Column("creation_time")
    private long creationTime;

    @Column("last_access_time")
    private long lastAccessTime;

    public ShortUrlEntity() {}

    public ShortUrlEntity(String shortKey, String originalUrl, long expirationTime) {
        this.shortKey = shortKey;
        this.originalUrl = originalUrl;
        this.expirationTime = expirationTime;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessTime = this.creationTime;
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

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public void updateLastAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }
}