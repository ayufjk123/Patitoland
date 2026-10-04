package com.patitoland.service;

import com.patitoland.model.Booking;
import com.patitoland.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleSheetsSyncTest {

    @Mock
    private BookingRepository bookingRepository;

    private GoogleSheetsSync sync;

    private static final LocalDateTime TS = LocalDateTime.of(2026, 8, 20, 10, 30, 0);
    private static final LocalDateTime RESERVATION = LocalDateTime.of(2026, 9, 5, 17, 0, 0);

    @BeforeEach
    void setUp() {
        sync = new GoogleSheetsSync(bookingRepository);
    }

    private Booking existingSheetBooking() {
        Booking b = new Booking();
        b.setId(42L);
        b.setTimestamp(TS);
        b.setPhone("  600123456 ");
        b.setParentName("María García");
        b.setReservationDateTime(RESERVATION);
        b.setSource("SHEET");
        b.setEmail("old@example.com");
        b.setChildrenNames("Pato");
        b.setChildrenCount("1");
        b.setRoomPreference("ZONA_RESTAURACION");
        b.setTariff("SIMPLE");
        b.setNotes("old notes");
        // POS payment data written via the internal API
        b.setDepositAmountCents(3000);
        b.setDepositStatus("COBRADO");
        b.setPaidAmountCents(12000);
        b.setPaymentStatus("PAGADO");
        b.setPosDepositOrderId("dep-123");
        b.setPosPaymentOrderId("pay-456");
        b.setDepositPaidAt(LocalDateTime.of(2026, 8, 21, 12, 0, 0));
        b.setPaidAt(LocalDateTime.of(2026, 9, 5, 20, 0, 0));
        b.setStatus("CONFIRMADA");
        return b;
    }

    private Booking csvBookingMatchingKey() {
        // Same identity key but different sheet-side fields; phone/parentName
        // differ only in whitespace/case to exercise normalization.
        Booking b = new Booking();
        b.setTimestamp(TS);
        b.setPhone("600123456");
        b.setParentName("maría garcía");
        b.setReservationDateTime(RESERVATION);
        b.setSource("SHEET");
        b.setEmail("new@example.com");
        b.setChildrenNames("Pato, Lola");
        b.setChildrenCount("2");
        b.setRoomPreference("SALA_PRIVADA");
        b.setTariff("COMPLETA");
        b.setNotes("new notes");
        return b;
    }

    @Test
    @DisplayName("Existing row: sheet fields updated, POS fields and id untouched")
    void existingBookingIsUpdatedWithoutTouchingPosFields() {
        Booking existing = existingSheetBooking();
        when(bookingRepository.findSheetSourced()).thenReturn(List.of(existing));

        sync.reconcileSheetBookings(List.of(csvBookingMatchingKey()));

        // Sheet-side fields refreshed on the managed entity
        assertEquals("new@example.com", existing.getEmail());
        assertEquals("Pato, Lola", existing.getChildrenNames());
        assertEquals("2", existing.getChildrenCount());
        assertEquals("SALA_PRIVADA", existing.getRoomPreference());
        assertEquals("COMPLETA", existing.getTariff());
        assertEquals("new notes", existing.getNotes());

        // id, status and every POS field preserved
        assertEquals(42L, existing.getId());
        assertEquals("CONFIRMADA", existing.getStatus());
        assertEquals(3000, existing.getDepositAmountCents());
        assertEquals("COBRADO", existing.getDepositStatus());
        assertEquals(12000, existing.getPaidAmountCents());
        assertEquals("PAGADO", existing.getPaymentStatus());
        assertEquals("dep-123", existing.getPosDepositOrderId());
        assertEquals("pay-456", existing.getPosPaymentOrderId());
        assertEquals(LocalDateTime.of(2026, 8, 21, 12, 0, 0), existing.getDepositPaidAt());
        assertEquals(LocalDateTime.of(2026, 9, 5, 20, 0, 0), existing.getPaidAt());

        // No insert, no delete
        verify(bookingRepository, never()).saveAll(any());
        verify(bookingRepository, never()).deleteAll(any());
        verify(bookingRepository, never()).deleteSheetSourced();
    }

    @Test
    @DisplayName("Row removed from CSV with no POS data is deleted")
    void removedFromCsvWithoutPosDataIsDeleted() {
        Booking existing = new Booking();
        existing.setId(7L);
        existing.setTimestamp(TS);
        existing.setPhone("600123456");
        existing.setParentName("María García");
        existing.setReservationDateTime(RESERVATION);
        existing.setSource("SHEET");
        when(bookingRepository.findSheetSourced()).thenReturn(List.of(existing));

        sync.reconcileSheetBookings(List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Booking>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookingRepository).deleteAll(captor.capture());
        assertEquals(List.of(existing), captor.getValue());
        verify(bookingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Row removed from CSV but with deposit is kept")
    void removedFromCsvWithDepositIsKept() {
        Booking existing = new Booking();
        existing.setId(9L);
        existing.setTimestamp(TS);
        existing.setPhone("600123456");
        existing.setParentName("María García");
        existing.setReservationDateTime(RESERVATION);
        existing.setSource("SHEET");
        existing.setDepositAmountCents(3000);
        when(bookingRepository.findSheetSourced()).thenReturn(List.of(existing));

        sync.reconcileSheetBookings(List.of());

        verify(bookingRepository, never()).deleteAll(any());
        verify(bookingRepository, never()).deleteSheetSourced();
        verify(bookingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("New CSV row with no DB match is inserted")
    void newCsvRowIsInserted() {
        when(bookingRepository.findSheetSourced()).thenReturn(List.of());
        Booking csvBooking = csvBookingMatchingKey();

        sync.reconcileSheetBookings(List.of(csvBooking));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Booking>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookingRepository).saveAll(captor.capture());
        assertEquals(List.of(csvBooking), captor.getValue());
        verify(bookingRepository, never()).deleteAll(any());
    }
}
