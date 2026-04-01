package com.patitoland.controller;

import com.patitoland.model.Booking;
import com.patitoland.model.BookingRequest;
import com.patitoland.repository.BookingRepository;
import com.patitoland.service.EmailService;
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

    private static final int BLOCK_HOURS = 3;
    private static final int ZONA_MAX_CONCURRENT = 2;

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

    public BookingController(BookingRepository bookingRepository, EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
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

        // Check 3-hour block window
        LocalDateTime windowStart = dateTime.minusHours(BLOCK_HOURS);
        LocalDateTime windowEnd = dateTime.plusHours(BLOCK_HOURS);

        List<Booking> conflicting = bookingRepository
                .findByRoomPreferenceAndReservationDateTimeBetween(room, windowStart, windowEnd);

        int maxAllowed = "SALA_PRIVADA".equals(room) ? 1 : ZONA_MAX_CONCURRENT;

        if (conflicting.size() >= maxAllowed) {
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
        booking.setTariff("WEB");
        booking.setNotes(request.getNotes() != null ? request.getNotes() : "");

        Booking saved = bookingRepository.save(booking);

        // Send emails asynchronously (non-blocking via @Async)
        emailService.sendBookingConfirmation(saved);
        emailService.sendBookingNotification(saved);

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

        // Group bookings by date
        Map<LocalDate, List<Booking>> bookingsByDate = bookings.stream()
                .filter(b -> b.getReservationDateTime() != null)
                .collect(Collectors.groupingBy(b -> b.getReservationDateTime().toLocalDate()));

        Map<String, Object> result = new LinkedHashMap<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Map.Entry<LocalDate, List<Booking>> entry : bookingsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<Booking> dayBookings = entry.getValue();
            boolean isWeekendOrHoliday = isWeekendOrHoliday(date);

            Map<String, Object> dayInfo = new LinkedHashMap<>();
            dayInfo.put("totalBookings", dayBookings.size());

            boolean privateRoomBooked = dayBookings.stream()
                    .anyMatch(b -> "SALA_PRIVADA".equals(b.getRoomPreference()));
            dayInfo.put("privateRoomBooked", privateRoomBooked);

            List<Map<String, String>> slots = dayBookings.stream()
                    .map(b -> {
                        Map<String, String> slot = new LinkedHashMap<>();
                        slot.put("time", b.getReservationDateTime().format(timeFormatter));
                        slot.put("room", b.getRoomPreference());
                        slot.put("tariff", b.getTariff());
                        return slot;
                    })
                    .collect(Collectors.toList());
            dayInfo.put("slots", slots);

            int maxBookings;
            boolean available;

            if (isWeekendOrHoliday) {
                // Weekend/holiday: max 3 per half-day, 6 total
                // Max 1 private room per half-day
                maxBookings = 6;

                List<Booking> morningBookings = dayBookings.stream()
                        .filter(b -> b.getReservationDateTime().getHour() < 15)
                        .collect(Collectors.toList());
                List<Booking> afternoonBookings = dayBookings.stream()
                        .filter(b -> b.getReservationDateTime().getHour() >= 15)
                        .collect(Collectors.toList());

                boolean morningFull = morningBookings.size() >= 3;
                boolean afternoonFull = afternoonBookings.size() >= 3;

                available = !morningFull || !afternoonFull;
            } else {
                // Weekday: max 3 total, max 1 private room
                maxBookings = 3;
                available = dayBookings.size() < 3;
            }

            dayInfo.put("maxBookings", maxBookings);
            dayInfo.put("available", available);

            result.put(date.format(dateFormatter), dayInfo);
        }

        return ResponseEntity.ok(result);
    }

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
