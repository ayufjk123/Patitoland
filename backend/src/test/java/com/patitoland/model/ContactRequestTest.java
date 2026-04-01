package com.patitoland.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContactRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ─── Getters / Setters ───────────────────────────────────────────

    @Test
    @DisplayName("Getters y setters funcionan correctamente")
    void testGettersAndSetters() {
        ContactRequest request = new ContactRequest();
        request.setName("Juan");
        request.setEmail("juan@example.com");
        request.setMessage("Hola mundo");

        assertEquals("Juan", request.getName());
        assertEquals("juan@example.com", request.getEmail());
        assertEquals("Hola mundo", request.getMessage());
    }

    // ─── Validaciones de campos requeridos ───────────────────────────

    @Test
    @DisplayName("Validación: todos los campos válidos → sin violaciones")
    void testValidRequest() {
        ContactRequest request = new ContactRequest();
        request.setName("Ana");
        request.setEmail("ana@example.com");
        request.setMessage("Me gustaría reservar");

        Set<ConstraintViolation<ContactRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "No debería haber violaciones con datos válidos");
    }

    @Test
    @DisplayName("Validación: nombre vacío → violación NotBlank")
    void testNameBlank() {
        ContactRequest request = new ContactRequest();
        request.setName("");
        request.setEmail("test@example.com");
        request.setMessage("Mensaje de prueba");

        Set<ConstraintViolation<ContactRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().equals("Name is required")));
    }

    @Test
    @DisplayName("Validación: nombre nulo → violación NotBlank")
    void testNameNull() {
        ContactRequest request = new ContactRequest();
        request.setName(null);
        request.setEmail("test@example.com");
        request.setMessage("Mensaje");

        Set<ConstraintViolation<ContactRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().equals("Name is required")));
    }

    @Test
    @DisplayName("Validación: email vacío → violación NotBlank")
    void testEmailBlank() {
        ContactRequest request = new ContactRequest();
        request.setName("Test");
        request.setEmail("");
        request.setMessage("Mensaje");

        Set<ConstraintViolation<ContactRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Validación: email con formato inválido → violación Email")
    void testEmailInvalidFormat() {
        ContactRequest request = new ContactRequest();
        request.setName("Test");
        request.setEmail("no-es-un-email");
        request.setMessage("Mensaje");

        Set<ConstraintViolation<ContactRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().equals("Invalid email format")));
    }

    @Test
    @DisplayName("Validación: mensaje vacío → violación NotBlank")
    void testMessageBlank() {
        ContactRequest request = new ContactRequest();
        request.setName("Test");
        request.setEmail("test@example.com");
        request.setMessage("");

        Set<ConstraintViolation<ContactRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().equals("Message is required")));
    }

    @Test
    @DisplayName("Validación: todos los campos vacíos → múltiples violaciones")
    void testAllFieldsBlank() {
        ContactRequest request = new ContactRequest();
        request.setName("");
        request.setEmail("");
        request.setMessage("");

        Set<ConstraintViolation<ContactRequest>> violations = validator.validate(request);
        assertTrue(violations.size() >= 3,
                "Debe haber al menos 3 violaciones cuando todos los campos están vacíos");
    }
}
