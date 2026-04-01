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
import java.util.List;

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

            bookingRepository.deleteAll();
            bookingRepository.saveAll(bookings);

            log.info("Sync complete. Saved {} bookings.", bookings.size());
        } catch (Exception e) {
            log.error("Error during Google Sheets sync", e);
        }
    }

    private List<Booking> parseCsv(String csvContent) throws Exception {
        List<Booking> bookings = new ArrayList<>();

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

                    bookings.add(booking);
                } catch (Exception e) {
                    log.warn("Failed to parse CSV row: {}", String.join(",", line), e);
                }
            }
        }

        return bookings;
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
