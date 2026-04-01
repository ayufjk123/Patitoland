package com.patitoland.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.patitoland.model.Booking;
import com.patitoland.model.ContactRequest;

import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${patitoland.contact.email:patitoland.parcinfantil@gmail.com}")
    private String contactEmail;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendContactEmail(ContactRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(contactEmail);
        message.setSubject("PatitoLand - Nuevo mensaje de contacto de " + request.getName());
        message.setFrom(contactEmail);
        message.setText(String.format(
            "Nombre: %s\nEmail: %s\n\nMensaje:\n%s",
            request.getName(),
            request.getEmail(),
            request.getMessage()
        ));
        message.setReplyTo(request.getEmail());
        mailSender.send(message);
    }

    public void sendBookingConfirmation(Booking booking) {
        String roomLabel = "SALA_PRIVADA".equals(booking.getRoomPreference())
                ? "Sala Privada" : "Zona Restauración";
        String date = booking.getReservationDateTime().format(DATE_FMT);
        String time = booking.getReservationDateTime().format(TIME_FMT);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(booking.getEmail());
        message.setFrom(contactEmail);
        message.setSubject("PatitoLand - Confirmación de reserva");
        message.setText(String.format(
            "¡Hola %s!\n\n" +
            "Tu reserva ha sido confirmada con los siguientes datos:\n\n" +
            "📅 Fecha: %s\n" +
            "🕐 Hora: %s\n" +
            "🏠 Zona: %s\n" +
            "👦 Niños: %s (aprox. %s)\n" +
            "📞 Teléfono: %s\n\n" +
            "¡Te esperamos en PatitoLand!\n" +
            "Carrer de Colom 453, Nave D52, Terrassa\n" +
            "Tel: 603 31 55 76",
            booking.getParentName(),
            date, time, roomLabel,
            booking.getChildrenNames(),
            booking.getChildrenCount(),
            booking.getPhone()
        ));
        mailSender.send(message);
    }

    public void sendBookingNotification(Booking booking) {
        String roomLabel = "SALA_PRIVADA".equals(booking.getRoomPreference())
                ? "Sala Privada" : "Zona Restauración";
        String date = booking.getReservationDateTime().format(DATE_FMT);
        String time = booking.getReservationDateTime().format(TIME_FMT);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(contactEmail);
        message.setFrom(contactEmail);
        message.setSubject("🎉 Nueva reserva - " + roomLabel + " " + date + " " + time);
        message.setText(String.format(
            "Nueva reserva recibida:\n\n" +
            "📅 Fecha: %s\n" +
            "🕐 Hora: %s\n" +
            "🏠 Zona: %s\n" +
            "👤 Padre/Madre: %s\n" +
            "📧 Email: %s\n" +
            "📞 Teléfono: %s\n" +
            "👦 Niños: %s (aprox. %s)\n" +
            "📝 Notas: %s",
            date, time, roomLabel,
            booking.getParentName(),
            booking.getEmail(),
            booking.getPhone(),
            booking.getChildrenNames(),
            booking.getChildrenCount(),
            booking.getNotes() != null ? booking.getNotes() : "-"
        ));
        mailSender.send(message);
    }
}
