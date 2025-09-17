package com.example.MailSafe.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthCheck {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> body = Map.of(
                "message", "Welcome to MailSafe API",
                "status", "success"
        );
        return new ResponseEntity<>(body, HttpStatus.OK); // 200
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> body = Map.of(
                "status", "OK"
        );
        return ResponseEntity.status(HttpStatus.OK).body(body); // 200
    }

    @PostMapping("/health")
    public ResponseEntity<String> healthCheckPost() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("Please use GET method for health check.");
    }

    @PutMapping("/health")
    public ResponseEntity<String> healthCheckPut() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("Please use GET method for health check.");
    }

    @DeleteMapping("/health")
    public ResponseEntity<String> healthCheckDelete() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("Please use GET method for health check.");
    }
}
