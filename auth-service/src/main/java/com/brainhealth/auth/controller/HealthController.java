package com.brainhealth.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/v1/system/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "auth-service");
    }

    @GetMapping("/api/v1/test/db")
    public Map<String, Object> testDb() {
        return Map.of("status", "OK", "message", "Test endpoint working");
    }
}
