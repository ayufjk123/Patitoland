package com.patitoland.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.patitoland.model.Booking;
import com.patitoland.model.ContactRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${patitoland.contact.email:patitoland.parcinfantil@gmail.com}")
    private String contactEmail;

    @Value("${patitoland.from.email:onboarding@resend.dev}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private void sendEmail(String to, String subject, String text, String replyTo) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Resend API key not configured, skipping email to {}", to);
            return;
        }

        String replyToField = replyTo != null
                ? String.format(",\"reply_to\":\"%s\"", escapeJson(replyTo))
                : "";

        String json = String.format(
                "{\"from\":\"%s\",\"to\":[\"%s\"],\"subject\":\"%s\",\"text\":\"%s\"%s}",
                escapeJson(fromEmail),
                escapeJson(to),
                escapeJson(subject),
                escapeJson(text),
                replyToField
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Email sent to {} - subject: {}", to, subject);
            } else {
                log.error("Resend API error ({}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public void sendTestEmail() {
        sendEmail(contactEmail, "PatitoLand - Test Email",
                "This is a test email from PatitoLand backend. If you receive this, email is working!", null);
    }

    public void sendContactEmail(ContactRequest request) {
        String text = String.format(
                "Nombre: %s\nEmail: %s\n\nMensaje:\n%s",
                request.getName(), request.getEmail(), request.getMessage()
        );
        sendEmail(contactEmail,
                "PatitoLand - Nuevo mensaje de contacto de " + request.getName(),
                text, request.getEmail());
    }

    @Async
    public void sendBookingConfirmation(Booking booking) {
        String roomLabel = "SALA_PRIVADA".equals(booking.getRoomPreference())
                ? "Sala Privada" : "Zona Restauracion";
        String date = booking.getReservationDateTime().format(DATE_FMT);
        String time = booking.getReservationDateTime().format(TIME_FMT);

        String text = String.format(
                "Hola %s!\n\n" +
                "Tu reserva ha sido confirmada con los siguientes datos:\n\n" +
                "Fecha: %s\n" +
                "Hora: %s\n" +
                "Zona: %s\n" +
                "Ninos: %s (aprox. %s)\n" +
                "Telefono: %s\n\n" +
                "Te esperamos en PatitoLand!\n" +
                "Carrer de Colom 453, Nave D52, Terrassa\n" +
                "Tel: 603 31 55 76",
                booking.getParentName(),
                date, time, roomLabel,
                booking.getChildrenNames(),
                booking.getChildrenCount(),
                booking.getPhone()
        );
        sendEmail(booking.getEmail(), "PatitoLand - Confirmacion de reserva", text, null);
    }

    @Async
    public void sendBookingNotification(Booking booking) {
        String roomLabel = "SALA_PRIVADA".equals(booking.getRoomPreference())
                ? "Sala Privada" : "Zona Restauracion";
        String date = booking.getReservationDateTime().format(DATE_FMT);
        String time = booking.getReservationDateTime().format(TIME_FMT);

        String text = String.format(
                "Nueva reserva recibida:\n\n" +
                "Fecha: %s\n" +
                "Hora: %s\n" +
                "Zona: %s\n" +
                "Padre/Madre: %s\n" +
                "Email: %s\n" +
                "Telefono: %s\n" +
                "Ninos: %s (aprox. %s)\n" +
                "Notas: %s",
                date, time, roomLabel,
                booking.getParentName(),
                booking.getEmail(),
                booking.getPhone(),
                booking.getChildrenNames(),
                booking.getChildrenCount(),
                booking.getNotes() != null ? booking.getNotes() : "-"
        );
        sendEmail(contactEmail, "Nueva reserva - " + roomLabel + " " + date + " " + time, text, null);
    }
}
