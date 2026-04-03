package com.patitoland.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.patitoland.model.Booking;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String TIMEZONE = "Europe/Madrid";
    private static final int EVENT_DURATION_HOURS = 1;

    @Value("${google.calendar.sala-privada:}")
    private String calendarSalaPrivada;

    @Value("${google.calendar.zona-restauracion:}")
    private String calendarZonaRestauracion;

    @Value("${google.credentials.json:}")
    private String credentialsJson;

    @Value("${google.impersonate.email:}")
    private String impersonateEmail;

    private Calendar calendarClient;

    @PostConstruct
    public void init() {
        if (credentialsJson == null || credentialsJson.isBlank()) {
            log.warn("Google credentials not configured, calendar sync disabled");
            return;
        }
        try {
            ServiceAccountCredentials saCredentials = ServiceAccountCredentials
                    .fromStream(new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)));

            GoogleCredentials credentials;
            if (impersonateEmail != null && !impersonateEmail.isBlank()) {
                credentials = saCredentials
                        .createDelegated(impersonateEmail)
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR_EVENTS));
                log.info("Using domain-wide delegation as: {}", impersonateEmail);
            } else {
                credentials = saCredentials
                        .createScoped(Collections.singleton(CalendarScopes.CALENDAR_EVENTS));
            }

            calendarClient = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("PatitoLand")
                    .build();

            log.info("Google Calendar client initialized - Sala Privada: {}, Zona Restauracion: {}",
                    calendarSalaPrivada, calendarZonaRestauracion);
        } catch (Exception e) {
            log.error("Failed to initialize Google Calendar client: {}", e.getMessage());
        }
    }

    @Async
    public void createBookingEvent(Booking booking) {
        if (calendarClient == null) {
            log.warn("Google Calendar not configured, skipping event creation");
            return;
        }
        try {
            String roomLabel = "SALA_PRIVADA".equals(booking.getRoomPreference())
                    ? "Sala Privada" : "Zona Restauracion";

            String targetCalendar = "SALA_PRIVADA".equals(booking.getRoomPreference())
                    ? calendarSalaPrivada : calendarZonaRestauracion;

            if (targetCalendar == null || targetCalendar.isBlank()) {
                log.warn("No calendar ID configured for room: {}", booking.getRoomPreference());
                return;
            }

            Event event = new Event()
                    .setSummary("Cumple " + booking.getParentName() + " " + booking.getChildrenCount() + " niños " + booking.getTariff())
                    .setLocation("Carrer de Colom 453, Nave D52, Terrassa")
                    .setDescription(buildDescription(booking, roomLabel));

            ZonedDateTime startZoned = booking.getReservationDateTime()
                    .atZone(ZoneId.of(TIMEZONE));
            ZonedDateTime endZoned = startZoned.plusHours(EVENT_DURATION_HOURS);

            event.setStart(toEventDateTime(startZoned));
            event.setEnd(toEventDateTime(endZoned));

            calendarClient.events().insert(targetCalendar, event).execute();
            log.info("Calendar event created for booking {} in calendar: {}", booking.getId(), targetCalendar);
        } catch (Exception e) {
            log.error("Failed to create calendar event for booking {}: {}",
                    booking.getId(), e.getMessage());
        }
    }

    public String testCalendar() {
        if (calendarClient == null) {
            return "Calendar client not initialized. Credentials JSON present: " +
                   (credentialsJson != null && !credentialsJson.isBlank()) +
                   ", Impersonate: " + impersonateEmail +
                   ", Sala Privada ID: " + calendarSalaPrivada +
                   ", Zona Rest ID: " + calendarZonaRestauracion;
        }
        StringBuilder sb = new StringBuilder();
        try {
            Event testEvent = new Event()
                    .setSummary("TEST - borrar")
                    .setDescription("Test event");
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(TIMEZONE)).plusDays(30);
            testEvent.setStart(toEventDateTime(now));
            testEvent.setEnd(toEventDateTime(now.plusHours(1)));

            calendarClient.events().insert(calendarSalaPrivada, testEvent).execute();
            sb.append("Sala Privada: OK. ");
        } catch (Exception e) {
            sb.append("Sala Privada ERROR: ").append(e.getMessage()).append(". ");
        }
        try {
            Event testEvent = new Event()
                    .setSummary("TEST - borrar")
                    .setDescription("Test event");
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(TIMEZONE)).plusDays(31);
            testEvent.setStart(toEventDateTime(now));
            testEvent.setEnd(toEventDateTime(now.plusHours(1)));

            calendarClient.events().insert(calendarZonaRestauracion, testEvent).execute();
            sb.append("Zona Restauracion: OK.");
        } catch (Exception e) {
            sb.append("Zona Restauracion ERROR: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private EventDateTime toEventDateTime(ZonedDateTime zdt) {
        return new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .setTimeZone(TIMEZONE);
    }

    private String buildDescription(Booking booking, String roomLabel) {
        return String.format(
                "Zona: %s\nPadre/Madre: %s\nEmail: %s\nTelefono: %s\nNinos: %s (aprox. %s)\nNotas: %s",
                roomLabel,
                booking.getParentName(),
                booking.getEmail(),
                booking.getPhone(),
                booking.getChildrenNames(),
                booking.getChildrenCount(),
                booking.getNotes() != null ? booking.getNotes() : "-"
        );
    }
}
