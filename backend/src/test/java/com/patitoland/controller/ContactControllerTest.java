package com.patitoland.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patitoland.model.ContactRequest;
import com.patitoland.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContactController.class)
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private ObjectMapper objectMapper;

    private ContactRequest createValidRequest() {
        ContactRequest request = new ContactRequest();
        request.setName("Pedro López");
        request.setEmail("pedro@example.com");
        request.setMessage("Quiero reservar para 10 niños");
        return request;
    }

    // ─── Happy path ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/contact con datos válidos → 200 OK + {status: sent}")
    void testSubmitContactSuccess() throws Exception {
        ContactRequest request = createValidRequest();

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"));

        verify(emailService, times(1)).sendContactEmail(any(ContactRequest.class));
    }

    // ─── Validaciones ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/contact con nombre vacío → 400 Bad Request")
    void testSubmitContactNameBlank() throws Exception {
        ContactRequest request = createValidRequest();
        request.setName("");

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactEmail(any());
    }

    @Test
    @DisplayName("POST /api/contact con email inválido → 400 Bad Request")
    void testSubmitContactInvalidEmail() throws Exception {
        ContactRequest request = createValidRequest();
        request.setEmail("no-valido");

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactEmail(any());
    }

    @Test
    @DisplayName("POST /api/contact con mensaje vacío → 400 Bad Request")
    void testSubmitContactMessageBlank() throws Exception {
        ContactRequest request = createValidRequest();
        request.setMessage("");

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactEmail(any());
    }

    @Test
    @DisplayName("POST /api/contact con body vacío → 400 Bad Request")
    void testSubmitContactEmptyBody() throws Exception {
        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactEmail(any());
    }

    @Test
    @DisplayName("POST /api/contact sin body → 400 Bad Request")
    void testSubmitContactNoBody() throws Exception {
        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(emailService, never()).sendContactEmail(any());
    }

    // ─── Verificación del servicio ───────────────────────────────────

    @Test
    @DisplayName("POST /api/contact: EmailService lanza excepción → ServletException propagada")
    void testSubmitContactServiceError() throws Exception {
        doThrow(new RuntimeException("SMTP fail"))
                .when(emailService).sendContactEmail(any());

        ContactRequest request = createValidRequest();

        assertThrows(jakarta.servlet.ServletException.class, () ->
                mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
        );
    }
}
