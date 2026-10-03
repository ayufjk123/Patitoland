package com.patitoland.repository;

import com.patitoland.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByReservationDateTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Booking> findByRoomPreferenceAndReservationDateTimeBetween(
            String roomPreference, LocalDateTime start, LocalDateTime end);

    /**
     * Deletes only Google Sheet-sourced bookings, preserving website bookings so the periodic
     * sheet sync refreshes sheet rows without wiping web reservations. A web booking is identified
     * by source = 'WEB' (current) OR the legacy tariff = 'WEB' marker (pre-source migration).
     */
    @Modifying
    @Query("delete from Booking b where (b.source is null or b.source <> 'WEB') and (b.tariff is null or b.tariff <> 'WEB')")
    void deleteSheetSourced();

    /** Fuzzy search across customer-facing fields for the internal POS API. */
    @Query("select b from Booking b where lower(b.parentName) like lower(concat('%', :q, '%'))"
            + " or lower(b.phone) like lower(concat('%', :q, '%'))"
            + " or lower(b.email) like lower(concat('%', :q, '%'))"
            + " or lower(b.childrenNames) like lower(concat('%', :q, '%'))"
            + " order by b.reservationDateTime desc")
    List<Booking> searchByText(@Param("q") String q);

    /** Most recent bookings, used by the internal search endpoint when no query is given. */
    List<Booking> findTop50ByOrderByReservationDateTimeDesc();
}
