package com.patitoland.service;

import com.patitoland.model.ContactRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private ContactRequest validRequest;

    @BeforeEach
    void setUp() {
        // Configurar el email de contacto usando ReflectionTestUtils
        ReflectionTestUtils.setField(emailService, "contactEmail", "test@patitoland.com");

        validRequest = new ContactRequest();
        validRequest.setName("María García");
        validRequest.setEmail("maria@example.com");
        validRequest.setMessage("Me gustaría información sobre fiestas de cumpleaños");
    }

    @Test
    @DisplayName("sendContactEmail envía un email con los datos correctos")
    void testSendContactEmailSuccess() {
        emailService.sendContactEmail(validRequest);

        // Verificar que mailSender.send() fue llamado exactamente una vez
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();

        // Verificar destinatario
        String[] to = sentMessage.getTo();
        assertNotNull(to);
        assertEquals("test@patitoland.com", to[0]);

        // Verificar asunto
        assertEquals("PatitoLand - Nuevo mensaje de contacto de María García",
                sentMessage.getSubject());

        // Verificar que el cuerpo contiene los datos del formulario
        String body = sentMessage.getText();
        assertNotNull(body);
        assertTrue(body.contains("María García"));
        assertTrue(body.contains("maria@example.com"));
        assertTrue(body.contains("Me gustaría información sobre fiestas de cumpleaños"));

        // Verificar replyTo
        assertEquals("maria@example.com", sentMessage.getReplyTo());
    }

    @Test
    @DisplayName("sendContactEmail: el cuerpo del email tiene el formato correcto")
    void testEmailBodyFormat() {
        emailService.sendContactEmail(validRequest);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        String expectedBody = String.format(
                "Nombre: %s\nEmail: %s\n\nMensaje:\n%s",
                "María García", "maria@example.com",
                "Me gustaría información sobre fiestas de cumpleaños"
        );

        assertEquals(expectedBody, captor.getValue().getText());
    }

    @Test
    @DisplayName("sendContactEmail: propaga excepciones del mailSender")
    void testSendContactEmailThrowsException() {
        doThrow(new RuntimeException("SMTP error"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(RuntimeException.class,
                () -> emailService.sendContactEmail(validRequest));
    }

    @Test
    @DisplayName("sendContactEmail: funciona con caracteres especiales en el nombre")
    void testSpecialCharactersInName() {
        validRequest.setName("José Ñoño");
        emailService.sendContactEmail(validRequest);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        String subject = captor.getValue().getSubject();
        assertNotNull(subject);
        assertTrue(subject.contains("José Ñoño"));

        String text = captor.getValue().getText();
        assertNotNull(text);
        assertTrue(text.contains("José Ñoño"));
    }
}
