package com.patitoland;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class PatitolandApplicationTest {

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("El contexto de Spring Boot se carga correctamente")
    void contextLoads() {
        // Si el contexto se carga sin excepciones, el test pasa
    }
}
