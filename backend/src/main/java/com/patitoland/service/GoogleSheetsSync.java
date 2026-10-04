package com.patitoland.service;

import com.opencsv.CSVReader;
import com.patitoland.model.Booking;
import com.patitoland.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GoogleSheetsSync {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsSync.class);

    private static final String SHEET_CSV_URL =
            "https://docs.google.com/spreadsheets/d/1LCizR_WNK3eDh8iA0exTghPt4x78scr5tVv7wxVdtjc/gviz/tq?tqx=out:csv";

    private static final DateTimeFormatter CSV_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d/MM/yyyy H:mm:ss");

    private final BookingRepository bookingRepository;

    public GoogleSheetsSync(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void syncFromGoogleSheets() {
        log.info("Starting Google Sheets sync...");
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SHEET_CSV_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch CSV. Status code: {}", response.statusCode());
                return;
            }

            List<Booking> bookings = parseCsv(response.body());

            // Upsert sheet-sourced rows by stable identity instead of delete-all + reinsert,
            // so POS payment data written to bookings rows (and their ids) survive the sync.
            reconcileSheetBookings(bookings);
        } catch (Exception e) {
            log.error("Error during Google Sheets sync", e);
        }
    }

    private List<Booking> parseCsv(String csvContent) throws Exception {
        List<Booking> bookings = new ArrayList<>();
        Set<BookingIdentity> seenBookings = new HashSet<>();

        try (CSVReader reader = new CSVReader(new java.io.StringReader(csvContent))) {
            // Skip header row
            String[] header = reader.readNext();
            if (header == null) return bookings;

            String[] line;
            while ((line = reader.readNext()) != null) {
                try {
                    if (line.length < 10) continue;

                    Booking booking = new Booking();

                    // Column 0: Timestamp
                    booking.setTimestamp(parseDateTime(line[0]));
                    // Column 1: Email
                    booking.setEmail(line[1].trim());
                    // Column 2: Phone
                    booking.setPhone(line[2].trim());
                    // Column 3: Parent name
                    booking.setParentName(line[3].trim());
                    // Column 4: Children names
                    booking.setChildrenNames(line[4].trim());
                    // Column 5: Children count
                    booking.setChildrenCount(line[5].trim());
                    // Column 6: Room preference
                    booking.setRoomPreference(mapRoom(line[6].trim()));
                    // Column 7: Reservation date/time
                    booking.setReservationDateTime(parseDateTime(line[7]));
                    // Column 8: Tariff
                    booking.setTariff(mapTariff(line[8].trim()));
                    // Column 9: Notes
                    booking.setNotes(line.length > 9 ? line[9].trim() : "");
                    booking.setSource("SHEET");

                    if (seenBookings.add(BookingIdentity.from(booking))) {
                        bookings.add(booking);
                    } else {
                        log.warn("Skipping duplicate Google Sheets booking at {}",
                                booking.getReservationDateTime());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse CSV row: {}", String.join(",", line), e);
                }
            }
        }

        return bookings;
    }

    /**
     * Reconciles parsed sheet bookings with the DB using a stable identity key
     * (timestamp + phone + parentName + reservationDateTime) instead of deleting and
     * reinserting all sheet rows. Existing rows keep their id and all POS payment
     * fields; only sheet-side fields are refreshed. Sheet rows missing from the CSV
     * are deleted only when they carry no POS payment data.
     */
    void reconcileSheetBookings(List<Booking> csvBookings) {
        List<Booking> existing = bookingRepository.findSheetSourced();

        Map<SheetKey, Booking> existingByKey = new HashMap<>();
        for (Booking b : existing) {
            existingByKey.putIfAbsent(SheetKey.from(b), b);
        }

        Set<SheetKey> csvKeys = new HashSet<>();
        List<Booking> toInsert = new ArrayList<>();
        int updated = 0;
        for (Booking csvBooking : csvBookings) {
            SheetKey key = SheetKey.from(csvBooking);
            csvKeys.add(key);
            Booking match = existingByKey.get(key);
            if (match != null) {
                // Update only sheet-side fields on the managed entity; never touch
                // id, status, or any POS payment field.
                match.setEmail(csvBooking.getEmail());
                match.setChildrenNames(csvBooking.getChildrenNames());
                match.setChildrenCount(csvBooking.getChildrenCount());
                match.setRoomPreference(csvBooking.getRoomPreference());
                match.setTariff(csvBooking.getTariff());
                match.setNotes(csvBooking.getNotes());
                updated++;
            } else {
                toInsert.add(csvBooking);
            }
        }
        if (!toInsert.isEmpty()) {
            bookingRepository.saveAll(toInsert);
        }

        List<Booking> toDelete = new ArrayList<>();
        int keptWithPosData = 0;
        for (Booking b : existing) {
            if (csvKeys.contains(SheetKey.from(b))) continue;
            if (hasPosPaymentData(b)) {
                keptWithPosData++;
                log.warn("Sheet booking id={} ('{}', {}) was removed from the sheet but has POS "
                        + "payment data; keeping it for manual review.",
                        b.getId(), b.getParentName(), b.getReservationDateTime());
            } else {
                toDelete.add(b);
            }
        }
        if (!toDelete.isEmpty()) {
            bookingRepository.deleteAll(toDelete);
        }

        log.info("Sheet sync complete: {} inserted, {} updated, {} deleted, {} kept (POS payment data).",
                toInsert.size(), updated, toDelete.size(), keptWithPosData);
    }

    private static boolean hasPosPaymentData(Booking b) {
        return b.getDepositAmountCents() != null
                || b.getPaidAmountCents() != null
                || b.getPosDepositOrderId() != null
                || b.getPosPaymentOrderId() != null;
    }

    /**
     * Stable identity of a sheet booking: the Google Form submission timestamp plus
     * phone, parent name, and reservation date/time. Strings are trimmed and
     * lowercased, null-safe.
     */
    private record SheetKey(
            LocalDateTime timestamp,
            String phone,
            String parentName,
            LocalDateTime reservationDateTime
    ) {
        private static SheetKey from(Booking booking) {
            return new SheetKey(
                    booking.getTimestamp(),
                    normalize(booking.getPhone()),
                    normalize(booking.getParentName()),
                    booking.getReservationDateTime()
            );
        }

        private static String normalize(String value) {
            return value == null ? null : value.trim().toLowerCase();
        }
    }

    private record BookingIdentity(
            String email,
            String phone,
            String parentName,
            String childrenNames,
            String childrenCount,
            String roomPreference,
            LocalDateTime reservationDateTime,
            String tariff,
            String notes
    ) {
        private static BookingIdentity from(Booking booking) {
            return new BookingIdentity(
                    booking.getEmail(),
                    booking.getPhone(),
                    booking.getParentName(),
                    booking.getChildrenNames(),
                    booking.getChildrenCount(),
                    booking.getRoomPreference(),
                    booking.getReservationDateTime(),
                    booking.getTariff(),
                    booking.getNotes()
            );
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return LocalDateTime.parse(value.trim(), CSV_DATE_FORMAT);
    }

    private String mapRoom(String value) {
        if (value == null) return "ZONA_RESTAURACION";
        return value.toLowerCase().contains("privada") ? "SALA_PRIVADA" : "ZONA_RESTAURACION";
    }

    private String mapTariff(String value) {
        if (value == null) return "SIMPLE";
        return value.toLowerCase().contains("completa") ? "COMPLETA" : "SIMPLE";
    }
}
