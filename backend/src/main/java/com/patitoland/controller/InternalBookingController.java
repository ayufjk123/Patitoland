package com.patitoland.controller;

import com.patitoland.model.Booking;
import com.patitoland.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Internal booking API consumed by the store POS backend
 * (docs/WEBSITE-INTERNAL-API.md in Patitoland-POS).
 * Every endpoint requires the X-Internal-Token header to match patitoland.internal-token;
 * a blank configured token rejects everything.
 *
 * Response shape mirrors the POS WebsiteBooking record
 * (id, customerName, children, timeSlot, depositDueCents, status);
 * extra detail fields are ignored by the POS deserializer.
 */
@RestController
@RequestMapping("/api/internal/bookings")
public class InternalBookingController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+)");
    private static final Set<String> VALID_STATUSES = Set.of("PENDIENTE", "CONFIRMADA", "COMPLETADA", "CANCELADA");
    private static final Set<String> VALID_DEPOSIT_METHODS = Set.of("TRANSFERENCIA", "EFECTIVO");

    private final BookingRepository bookingRepository;

    @Value("${patitoland.internal-token:}")
    private String internalToken;

    /** Party slot length in hours (contract example "11:00-13:00"). */
    @Value("${patitoland.booking-slot-hours:2}")
    private int slotHours;

    /** Deposit due (cents) shown to the POS until the deposit is actually collected. */
    @Value("${patitoland.deposit-due-cents:5000}")
    private int depositDueCents;

    public InternalBookingController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    private boolean authorized(String token) {
        return internalToken != null && !internalToken.isBlank() && internalToken.equals(token);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }

    /** Bookings whose reservationDateTime falls today, ascending by time. */
    @GetMapping("/today")
    public ResponseEntity<?> today(@RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!authorized(token)) return unauthorized();
        LocalDate today = LocalDate.now();
        List<Booking> bookings = bookingRepository
                .findByReservationDateTimeBetween(today.atStartOfDay(), today.atTime(23, 59, 59))
                .stream()
                .filter(b -> b.getReservationDateTime() != null)
                .sorted(Comparator.comparing(Booking::getReservationDateTime))
                .collect(Collectors.toList());
        return ResponseEntity.ok(toDtoList(bookings));
    }

    /** Fuzzy search by parent name / phone / email / children names; empty q -> latest 50. */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                    @RequestParam(value = "q", required = false) String q) {
        if (!authorized(token)) return unauthorized();
        List<Booking> bookings = (q == null || q.isBlank())
                ? bookingRepository.findTop50ByOrderByReservationDateTimeDesc()
                : bookingRepository.searchByText(q.trim());
        return ResponseEntity.ok(toDtoList(bookings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                 @PathVariable String id) {
        if (!authorized(token)) return unauthorized();
        Optional<Booking> found = findByStringId(id);
        if (found.isEmpty()) return notFound();
        return ResponseEntity.ok(toDto(found.get()));
    }

    /** Records that the deposit was collected at the POS. Idempotent on COBRADO. */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<?> deposit(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                     @PathVariable String id,
                                     @RequestBody Map<String, Object> body) {
        if (!authorized(token)) return unauthorized();
        Optional<Booking> found = findByStringId(id);
        if (found.isEmpty()) return notFound();
        Integer amountCents = amountCents(body);
        if (amountCents == null || amountCents <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "amountCents must be a positive integer"));
        }
        Booking b = found.get();
        // Idempotent: once COBRADO, return current state without recording again;
        // only backfill method/reference if they were never recorded.
        if ("COBRADO".equals(b.getDepositStatus())) {
            boolean dirty = false;
            String method = depositMethod(body);
            if (method != null && b.getDepositMethod() == null) {
                b.setDepositMethod(method);
                dirty = true;
            }
            String reference = reference(body);
            if (reference != null && b.getDepositReference() == null) {
                b.setDepositReference(reference);
                dirty = true;
            }
            if (dirty) bookingRepository.save(b);
            return ResponseEntity.ok(toDto(b));
        }
        b.setDepositAmountCents(amountCents);
        b.setDepositStatus("COBRADO");
        b.setDepositMethod(depositMethod(body));
        b.setDepositReference(reference(body));
        String posOrderId = posOrderId(body);
        if (posOrderId != null) b.setPosDepositOrderId(posOrderId);
        b.setDepositPaidAt(LocalDateTime.now());
        // Lifecycle: collecting the deposit confirms a pending booking.
        if (b.getStatus() == null || "PENDIENTE".equals(b.getStatus())) {
            b.setStatus("CONFIRMADA");
        }
        bookingRepository.save(b);
        return ResponseEntity.ok(toDto(b));
    }

    /** Records that the final payment was collected at the POS. Idempotent on PAGADO. */
    @PostMapping("/{id}/payment")
    public ResponseEntity<?> payment(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                     @PathVariable String id,
                                     @RequestBody Map<String, Object> body) {
        if (!authorized(token)) return unauthorized();
        Optional<Booking> found = findByStringId(id);
        if (found.isEmpty()) return notFound();
        Integer amountCents = amountCents(body);
        if (amountCents == null || amountCents <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "amountCents must be a positive integer"));
        }
        Booking b = found.get();
        // Idempotent: once PAGADO, return current state without recording again.
        if ("PAGADO".equals(b.getPaymentStatus())) {
            return ResponseEntity.ok(toDto(b));
        }
        b.setPaidAmountCents(amountCents);
        b.setPaymentStatus("PAGADO");
        String posOrderId = posOrderId(body);
        if (posOrderId != null) b.setPosPaymentOrderId(posOrderId);
        b.setPaidAt(LocalDateTime.now());
        // Lifecycle: full payment completes the booking.
        b.setStatus("COMPLETADA");
        bookingRepository.save(b);
        return ResponseEntity.ok(toDto(b));
    }

    /**
     * Manually sets the booking lifecycle status (e.g. CANCELADA from the store).
     * Does not touch deposit/payment fields — refunds are handled separately.
     */
    @PostMapping("/{id}/status")
    public ResponseEntity<?> setStatus(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                       @PathVariable String id,
                                       @RequestBody Map<String, Object> body) {
        if (!authorized(token)) return unauthorized();
        Optional<Booking> found = findByStringId(id);
        if (found.isEmpty()) return notFound();
        Object raw = body.get("status");
        String status = raw != null ? raw.toString().trim().toUpperCase(Locale.ROOT) : "";
        if (!VALID_STATUSES.contains(status)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "status must be one of " + VALID_STATUSES.stream().sorted().collect(Collectors.toList())));
        }
        Booking b = found.get();
        b.setStatus(status);
        bookingRepository.save(b);
        return ResponseEntity.ok(toDto(b));
    }

    // --- helpers ---

    private Optional<Booking> findByStringId(String id) {
        try {
            return bookingRepository.findById(Long.parseLong(id.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private ResponseEntity<Map<String, Object>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Booking not found"));
    }

    private Integer amountCents(Map<String, Object> body) {
        Object value = body.get("amountCents");
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.valueOf(value.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /** POS sends posOrderId as a JSON number (nullable); stored as String. */
    private String posOrderId(Map<String, Object> body) {
        Object value = body.get("posOrderId");
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Deposit collection method. Only TRANSFERENCIA / EFECTIVO are accepted;
     * anything else (or blank) is ignored as null for backward compatibility.
     */
    private String depositMethod(Map<String, Object> body) {
        Object value = body.get("method");
        if (value == null) return null;
        String text = value.toString().trim().toUpperCase(Locale.ROOT);
        return VALID_DEPOSIT_METHODS.contains(text) ? text : null;
    }

    /** Optional transfer reference; trimmed, blank becomes null. */
    private String reference(Map<String, Object> body) {
        Object value = body.get("reference");
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private List<Map<String, Object>> toDtoList(List<Booking> bookings) {
        return bookings.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * First six fields match the POS WebsiteBooking record exactly;
     * the rest are extra detail the POS currently ignores.
     */
    private Map<String, Object> toDto(Booking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(b.getId()));
        m.put("customerName", b.getParentName());
        m.put("children", parseChildren(b.getChildrenCount()));
        m.put("timeSlot", timeSlot(b.getReservationDateTime()));
        // Until the deposit is charged, the POS sees the configured due amount; after, the actual amount.
        m.put("depositDueCents", "COBRADO".equals(b.getDepositStatus()) && b.getDepositAmountCents() != null
                ? b.getDepositAmountCents().longValue() : (long) depositDueCents);
        m.put("status", b.getStatus() != null ? b.getStatus() : "PENDIENTE");
        // extra detail
        m.put("depositStatus", b.getDepositStatus() != null ? b.getDepositStatus() : "PENDIENTE");
        m.put("depositAmountCents", b.getDepositAmountCents());
        m.put("depositMethod", b.getDepositMethod());
        m.put("depositReference", b.getDepositReference());
        m.put("paymentStatus", b.getPaymentStatus() != null ? b.getPaymentStatus() : "PENDIENTE");
        m.put("paidAmountCents", b.getPaidAmountCents());
        m.put("posDepositOrderId", b.getPosDepositOrderId());
        m.put("posPaymentOrderId", b.getPosPaymentOrderId());
        m.put("depositPaidAt", b.getDepositPaidAt() != null ? b.getDepositPaidAt().toString() : null);
        m.put("paidAt", b.getPaidAt() != null ? b.getPaidAt().toString() : null);
        m.put("email", b.getEmail());
        m.put("phone", b.getPhone());
        m.put("childrenNames", b.getChildrenNames());
        m.put("roomPreference", b.getRoomPreference());
        m.put("tariff", b.getTariff());
        m.put("reservationDateTime", b.getReservationDateTime() != null ? b.getReservationDateTime().toString() : null);
        return m;
    }

    /** childrenCount is free text on the website ("10", "10-15"); take the leading number. */
    private Integer parseChildren(String childrenCount) {
        if (childrenCount == null) return null;
        Matcher matcher = LEADING_NUMBER.matcher(childrenCount);
        if (!matcher.find()) return null;
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String timeSlot(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.toLocalTime().format(TIME_FORMAT)
                + "-" + dateTime.toLocalTime().plusHours(slotHours).format(TIME_FORMAT);
    }
}
