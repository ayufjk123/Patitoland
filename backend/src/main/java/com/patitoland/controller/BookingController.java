package com.patitoland.controller;

import com.patitoland.model.Booking;
import com.patitoland.model.BookingRequest;
import com.patitoland.repository.BookingRepository;
import com.patitoland.service.EmailService;
import com.patitoland.service.GoogleCalendarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    private final GoogleCalendarService googleCalendarService;

    private static final int BLOCK_HOURS = 3;
    private static final int ZONA_MAX_CONCURRENT = 2;

    // Calendar events outside these hours are treated as noise (e.g. 00:00 / 06:00 junk entries)
    // and ignored when merging the calendar into availability.
    private static final LocalTime BUSINESS_OPEN = LocalTime.of(9, 0);
    private static final LocalTime BUSINESS_CLOSE = LocalTime.of(22, 0);

    private static final Set<LocalDate> HOLIDAYS = Set.of(
            // 2025 holidays (national + Catalonia)
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 6),
            LocalDate.of(2025, 4, 18),
            LocalDate.of(2025, 4, 21),
            LocalDate.of(2025, 5, 1),
            LocalDate.of(2025, 6, 24),
            LocalDate.of(2025, 8, 15),
            LocalDate.of(2025, 9, 11),
            LocalDate.of(2025, 10, 12),
            LocalDate.of(2025, 11, 1),
            LocalDate.of(2025, 12, 6),
            LocalDate.of(2025, 12, 8),
            LocalDate.of(2025, 12, 25),
            LocalDate.of(2025, 12, 26),
            // 2026 holidays (national + Catalonia)
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 6),
            LocalDate.of(2026, 4, 3),
            LocalDate.of(2026, 4, 6),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 24),
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 9, 11),
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 6),
            LocalDate.of(2026, 12, 8),
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2026, 12, 26)
    );

    public BookingController(BookingRepository bookingRepository, EmailService emailService,
                             GoogleCalendarService googleCalendarService) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
        this.googleCalendarService = googleCalendarService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBooking(@PathVariable Long id) {
        if (!bookingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Booking deleted"));
    }

    @PostMapping
    public ResponseEntity<?> createBooking(@Valid @RequestBody BookingRequest request) {
        String room = request.getRoomPreference();
        LocalDateTime dateTime = request.getReservationDateTime();

        if (!"SALA_PRIVADA".equals(room) && !"ZONA_RESTAURACION".equals(room)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid room type"));
        }

        // Check the 3-hour block window against BOTH sources: the DB (web + sheet bookings)
        // and the Google Calendar (manual + phone entries). Reject if EITHER source is already
        // full, so manual calendar entries also prevent a conflicting online booking.
        LocalDateTime windowStart = dateTime.minusHours(BLOCK_HOURS);
        LocalDateTime windowEnd = dateTime.plusHours(BLOCK_HOURS);

        long dbConflicting = bookingRepository
                .findByRoomPreferenceAndReservationDateTimeBetween(room, windowStart, windowEnd)
                .size();

        // listBookedSlots degrades to empty on calendar failure, so booking still works if the
        // calendar is unreachable (the DB check remains).
        long calConflicting = googleCalendarService.listBookedSlots(windowStart, windowEnd).stream()
                .filter(s -> room.equals(s.room()))
                .count();

        int maxAllowed = "SALA_PRIVADA".equals(room) ? 1 : ZONA_MAX_CONCURRENT;

        if (dbConflicting >= maxAllowed || calConflicting >= maxAllowed) {
            String msg = "SALA_PRIVADA".equals(room)
                    ? "Sala privada is not available within this 3-hour window"
                    : "Zona restauración has reached the maximum of 2 bookings within this 3-hour window";
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg));
        }

        Booking booking = new Booking();
        booking.setTimestamp(LocalDateTime.now());
        booking.setParentName(request.getParentName());
        booking.setEmail(request.getEmail());
        booking.setPhone(request.getPhone());
        booking.setChildrenNames(request.getChildrenNames());
        booking.setChildrenCount(request.getChildrenCount());
        booking.setRoomPreference(room);
        booking.setReservationDateTime(dateTime);
        // Customer-selected party package; default to COMPLETA if not provided.
        String tariff = request.getTariff();
        booking.setTariff("SIMPLE".equals(tariff) || "COMPLETA".equals(tariff) ? tariff : "COMPLETA");
        booking.setSource("WEB");
        booking.setNotes(request.getNotes() != null ? request.getNotes() : "");

        Booking saved = bookingRepository.save(booking);

        // Send emails and create calendar event asynchronously (non-blocking via @Async)
        emailService.sendBookingConfirmation(saved);
        emailService.sendBookingNotification(saved);
        googleCalendarService.createBookingEvent(saved);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(),
                "message", "Booking created successfully"
        ));
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> getAvailability(@RequestParam String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Booking> bookings = bookingRepository.findByReservationDateTimeBetween(start, end);

        // Two independent sources of truth (currently inconsistent): the DB (online form +
        // Google Sheet sync) and the Google Calendar (manual + phone + walk-in entries).
        // We can't reliably de-duplicate them, so instead of merging we evaluate each
        // source's availability separately and require BOTH to agree there is room.
        // This is conservative (a day may show full if either source is full) but never
        // allows a double-booking, and manual calendar entries now reduce availability.
        Map<LocalDate, List<Slot>> dbByDate = new HashMap<>();
        for (Booking b : bookings) {
            if (b.getReservationDateTime() == null) continue;
            dbByDate.computeIfAbsent(b.getReservationDateTime().toLocalDate(), k -> new ArrayList<>())
                    .add(new Slot(b.getReservationDateTime().toLocalTime(), b.getRoomPreference(), b.getTariff()));
        }

        Map<LocalDate, List<Slot>> calByDate = new HashMap<>();
        for (GoogleCalendarService.BookedSlot cs : googleCalendarService.listBookedSlots(start, end)) {
            LocalTime t = cs.time();
            if (t.isBefore(BUSINESS_OPEN) || t.isAfter(BUSINESS_CLOSE)) continue; // drop noise events
            calByDate.computeIfAbsent(cs.date(), k -> new ArrayList<>())
                    .add(new Slot(t, cs.room(), "MANUAL"));
        }

        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(dbByDate.keySet());
        allDates.addAll(calByDate.keySet());

        Map<String, Object> result = new LinkedHashMap<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (LocalDate date : allDates) {
            List<Slot> dbSlots = dbByDate.getOrDefault(date, List.of());
            List<Slot> calSlots = calByDate.getOrDefault(date, List.of());
            boolean isWeekendOrHoliday = isWeekendOrHoliday(date);

            // Available only if BOTH sources have room (conservative, never double-books).
            boolean available = hasRoom(isWeekendOrHoliday, dbSlots) && hasRoom(isWeekendOrHoliday, calSlots);

            int totalBookings = Math.max(dbSlots.size(), calSlots.size());
            boolean privateRoomBooked =
                    dbSlots.stream().anyMatch(s -> "SALA_PRIVADA".equals(s.room()))
                 || calSlots.stream().anyMatch(s -> "SALA_PRIVADA".equals(s.room()));

            // Show the busier source's booked times (avoids showing the same booking twice).
            List<Slot> shown = calSlots.size() > dbSlots.size() ? calSlots : dbSlots;
            List<Map<String, String>> slots = shown.stream()
                    .map(s -> {
                        Map<String, String> slot = new LinkedHashMap<>();
                        slot.put("time", s.time().format(timeFormatter));
                        slot.put("room", s.room());
                        slot.put("tariff", s.tariff());
                        return slot;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> dayInfo = new LinkedHashMap<>();
            dayInfo.put("totalBookings", totalBookings);
            dayInfo.put("privateRoomBooked", privateRoomBooked);
            dayInfo.put("slots", slots);
            dayInfo.put("maxBookings", isWeekendOrHoliday ? 6 : 3);
            dayInfo.put("available", available);

            result.put(date.format(dateFormatter), dayInfo);
        }

        return ResponseEntity.ok(result);
    }

    /** Capacity rule for one source on one day: weekday = 3/day; weekend/holiday = 3 per half-day. */
    private boolean hasRoom(boolean isWeekendOrHoliday, List<Slot> slots) {
        if (isWeekendOrHoliday) {
            long morning = slots.stream().filter(s -> s.time().getHour() < 15).count();
            long afternoon = slots.stream().filter(s -> s.time().getHour() >= 15).count();
            return morning < 3 || afternoon < 3;
        }
        return slots.size() < 3;
    }

    /** An effective booked time slot from either source. */
    private record Slot(LocalTime time, String room, String tariff) {}

    @GetMapping("/date/{date}")
    public ResponseEntity<List<Map<String, Object>>> getBookingsByDate(@PathVariable String date) {
        LocalDate localDate = LocalDate.parse(date);
        LocalDateTime start = localDate.atStartOfDay();
        LocalDateTime end = localDate.atTime(23, 59, 59);

        List<Booking> bookings = bookingRepository.findByReservationDateTimeBetween(start, end);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        List<Map<String, Object>> result = bookings.stream()
                .map(b -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id", b.getId());
                    info.put("parentName", b.getParentName());
                    info.put("childrenNames", b.getChildrenNames());
                    info.put("childrenCount", b.getChildrenCount());
                    info.put("time", b.getReservationDateTime() != null
                            ? b.getReservationDateTime().format(timeFormatter) : null);
                    info.put("room", b.getRoomPreference());
                    info.put("tariff", b.getTariff());
                    info.put("email", b.getEmail());
                    info.put("phone", b.getPhone());
                    info.put("notes", b.getNotes());
                    return info;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private boolean isWeekendOrHoliday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY || HOLIDAYS.contains(date);
    }
}
