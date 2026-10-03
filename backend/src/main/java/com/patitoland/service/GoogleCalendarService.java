package com.patitoland.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String TIMEZONE = "Europe/Madrid";
    private static final int EVENT_DURATION_HOURS = 2;

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
        String targetCalendar = "SALA_PRIVADA".equals(booking.getRoomPreference())
                ? calendarSalaPrivada : calendarZonaRestauracion;
        if (targetCalendar == null || targetCalendar.isBlank()) {
            log.warn("No calendar ID configured for room: {}", booking.getRoomPreference());
            return;
        }

        String roomLabel = "SALA_PRIVADA".equals(booking.getRoomPreference())
                ? "Sala Privada" : "Zona Restauracion";
        Event event = new Event()
                .setSummary("Cumple " + booking.getParentName() + " " + booking.getChildrenCount() + " niños " + booking.getTariff())
                .setLocation("Carrer de Colom 453, Nave D52, Terrassa")
                .setDescription(buildDescription(booking, roomLabel));
        ZonedDateTime startZoned = booking.getReservationDateTime().atZone(ZoneId.of(TIMEZONE));
        event.setStart(toEventDateTime(startZoned));
        event.setEnd(toEventDateTime(startZoned.plusHours(EVENT_DURATION_HOURS)));

        // Retry to survive transient Google outages (brief token/network failures).
        // Without this, a single failure silently drops the event with no second attempt
        // (this is exactly how a booking ended up missing from the calendar).
        long[] delaysMs = {0, 30_000, 120_000, 300_000, 600_000, 900_000}; // spread over ~15 min
        for (int attempt = 0; attempt < delaysMs.length; attempt++) {
            if (delaysMs[attempt] > 0) {
                try {
                    Thread.sleep(delaysMs[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                calendarClient.events().insert(targetCalendar, event).execute();
                log.info("Calendar event created for booking {} in calendar: {} (attempt {})",
                        booking.getId(), targetCalendar, attempt + 1);
                return;
            } catch (Exception e) {
                log.warn("Calendar event attempt {}/{} failed for booking {}: {}",
                        attempt + 1, delaysMs.length, booking.getId(), e.getMessage());
            }
        }
        log.error("Gave up creating calendar event for booking {} after {} attempts",
                booking.getId(), delaysMs.length);
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
                "Zona: %s\nDuracion: 2 horas\nPadre/Madre: %s\nEmail: %s\nTelefono: %s\nNinos: %s (aprox. %s)\nNotas: %s",
                roomLabel,
                booking.getParentName(),
                booking.getEmail(),
                booking.getPhone(),
                booking.getChildrenNames(),
                booking.getChildrenCount(),
                booking.getNotes() != null ? booking.getNotes() : "-"
        );
    }

    /** A timed event read back from a booking calendar (used to merge manual entries into availability). */
    public record BookedSlot(LocalDate date, LocalTime time, String room, String summary) {}

    /**
     * Lists timed events from both booking calendars in the given window.
     * All-day events (holidays, multi-day accommodation blocks) are skipped, so only
     * real time-slot bookings are returned. Returns an empty list if the calendar
     * client is unavailable, so callers degrade gracefully to DB-only availability.
     */
    public List<BookedSlot> listBookedSlots(LocalDateTime from, LocalDateTime to) {
        List<BookedSlot> slots = new ArrayList<>();
        if (calendarClient == null) {
            return slots;
        }
        collectSlots(slots, calendarSalaPrivada, "SALA_PRIVADA", from, to);
        collectSlots(slots, calendarZonaRestauracion, "ZONA_RESTAURACION", from, to);
        return slots;
    }

    private void collectSlots(List<BookedSlot> slots, String calendarId, String room,
                              LocalDateTime from, LocalDateTime to) {
        if (calendarId == null || calendarId.isBlank()) {
            return;
        }
        try {
            ZoneId zone = ZoneId.of(TIMEZONE);
            com.google.api.client.util.DateTime timeMin =
                    new com.google.api.client.util.DateTime(Date.from(from.atZone(zone).toInstant()));
            com.google.api.client.util.DateTime timeMax =
                    new com.google.api.client.util.DateTime(Date.from(to.atZone(zone).toInstant()));

            Events events = calendarClient.events().list(calendarId)
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setMaxResults(2500)
                    .execute();

            List<Event> items = events.getItems();
            if (items == null) {
                return;
            }
            for (Event e : items) {
                EventDateTime start = e.getStart();
                // Skip all-day events (date set, dateTime null): holidays, accommodation, etc.
                if (start == null || start.getDateTime() == null) {
                    continue;
                }
                ZonedDateTime z = Instant.ofEpochMilli(start.getDateTime().getValue()).atZone(zone);
                slots.add(new BookedSlot(z.toLocalDate(), z.toLocalTime(), room, e.getSummary()));
            }
        } catch (Exception e) {
            log.error("Failed to list calendar events for {}: {}", room, e.getMessage());
        }
    }
}
