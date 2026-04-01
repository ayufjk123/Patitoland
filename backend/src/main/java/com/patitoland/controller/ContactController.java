package com.patitoland.controller;

import com.patitoland.model.ContactRequest;
import com.patitoland.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class ContactController {

    private final EmailService emailService;

    public ContactController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/contact")
    public ResponseEntity<Map<String, String>> submitContact(@Valid @RequestBody ContactRequest request) {
        emailService.sendContactEmail(request);
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    @GetMapping("/test-email")
    public ResponseEntity<Map<String, String>> testEmail() {
        try {
            emailService.sendTestEmail();
            return ResponseEntity.ok(Map.of("status", "Email sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }
}
