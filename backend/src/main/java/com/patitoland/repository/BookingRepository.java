package com.patitoland.repository;

import com.patitoland.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByReservationDateTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Booking> findByRoomPreferenceAndReservationDateTimeBetween(
            String roomPreference, LocalDateTime start, LocalDateTime end);
}
