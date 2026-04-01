package com.patitoland.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    private String email;

    private String phone;

    private String parentName;

    private String childrenNames;

    private String childrenCount;

    private String roomPreference;

    private LocalDateTime reservationDateTime;

    private String tariff;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public Booking() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getChildrenNames() { return childrenNames; }
    public void setChildrenNames(String childrenNames) { this.childrenNames = childrenNames; }

    public String getChildrenCount() { return childrenCount; }
    public void setChildrenCount(String childrenCount) { this.childrenCount = childrenCount; }

    public String getRoomPreference() { return roomPreference; }
    public void setRoomPreference(String roomPreference) { this.roomPreference = roomPreference; }

    public LocalDateTime getReservationDateTime() { return reservationDateTime; }
    public void setReservationDateTime(LocalDateTime reservationDateTime) { this.reservationDateTime = reservationDateTime; }

    public String getTariff() { return tariff; }
    public void setTariff(String tariff) { this.tariff = tariff; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
