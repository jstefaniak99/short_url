package com.example.cleanup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CleanupApplication {
    public static void main(String[] args) {
        SpringApplication.run(CleanupApplication.class, args);
    }
}