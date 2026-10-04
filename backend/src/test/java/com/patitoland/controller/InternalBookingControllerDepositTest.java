package com.patitoland.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patitoland.model.Booking;
import com.patitoland.repository.BookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalBookingController.class)
@TestPropertySource(properties = "patitoland.internal-token=test-token")
class InternalBookingControllerDepositTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingRepository bookingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Booking pendingBooking(long id) {
        Booking b = new Booking();
        b.setId(id);
        b.setStatus("PENDIENTE");
        return b;
    }

    @Test
    @DisplayName("POST deposit con method=TRANSFERENCIA y reference → guarda ambos campos")
    void testDepositWithTransferMethodAndReference() throws Exception {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pendingBooking(1L)));

        mockMvc.perform(post("/api/internal/bookings/1/deposit")
                        .header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountCents", 5000,
                                "method", "TRANSFERENCIA",
                                "reference", "  TRX-12345  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositMethod").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.depositReference").value("TRX-12345"))
                .andExpect(jsonPath("$.depositStatus").value("COBRADO"));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertEquals("TRANSFERENCIA", saved.getDepositMethod());
        assertEquals("TRX-12345", saved.getDepositReference());
    }

    @Test
    @DisplayName("POST deposit con method inválido → se ignora a null, sin error")
    void testDepositWithInvalidMethodIgnored() throws Exception {
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(pendingBooking(2L)));

        mockMvc.perform(post("/api/internal/bookings/2/deposit")
                        .header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountCents", 5000,
                                "method", "TARJETA",
                                "reference", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositMethod").doesNotExist())
                .andExpect(jsonPath("$.depositReference").doesNotExist())
                .andExpect(jsonPath("$.depositStatus").value("COBRADO"));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertNull(captor.getValue().getDepositMethod());
        assertNull(captor.getValue().getDepositReference());
    }

    @Test
    @DisplayName("POST deposit idempotente (ya COBRADO) → backfill de method/reference si eran null")
    void testDepositIdempotentBackfillsMethodAndReference() throws Exception {
        Booking b = pendingBooking(3L);
        b.setDepositStatus("COBRADO");
        b.setDepositAmountCents(5000);
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(b));

        mockMvc.perform(post("/api/internal/bookings/3/deposit")
                        .header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountCents", 5000,
                                "method", "efectivo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositMethod").value("EFECTIVO"))
                .andExpect(jsonPath("$.depositStatus").value("COBRADO"));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertEquals("EFECTIVO", captor.getValue().getDepositMethod());
    }

    @Test
    @DisplayName("POST deposit idempotente con method ya registrado → no pisa ni guarda")
    void testDepositIdempotentKeepsExistingMethod() throws Exception {
        Booking b = pendingBooking(4L);
        b.setDepositStatus("COBRADO");
        b.setDepositAmountCents(5000);
        b.setDepositMethod("TRANSFERENCIA");
        b.setDepositReference("TRX-999");
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(b));

        mockMvc.perform(post("/api/internal/bookings/4/deposit")
                        .header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountCents", 5000,
                                "method", "EFECTIVO",
                                "reference", "OTHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositMethod").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.depositReference").value("TRX-999"));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST deposit sin method/reference → 200 OK, campos quedan null (backward compatible)")
    void testDepositWithoutMethodIsBackwardCompatible() throws Exception {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(pendingBooking(5L)));

        mockMvc.perform(post("/api/internal/bookings/5/deposit")
                        .header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amountCents", 5000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositStatus").value("COBRADO"));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertNull(captor.getValue().getDepositMethod());
        assertNull(captor.getValue().getDepositReference());
    }
}
