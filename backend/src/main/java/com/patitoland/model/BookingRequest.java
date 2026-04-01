package com.patitoland.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class BookingRequest {

    @NotBlank(message = "Parent name is required")
    private String parentName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "Children names are required")
    private String childrenNames;

    @NotBlank(message = "Children count is required")
    private String childrenCount;

    @NotBlank(message = "Room preference is required")
    private String roomPreference;

    @NotNull(message = "Reservation date/time is required")
    private LocalDateTime reservationDateTime;

    private String notes;

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getChildrenNames() { return childrenNames; }
    public void setChildrenNames(String childrenNames) { this.childrenNames = childrenNames; }

    public String getChildrenCount() { return childrenCount; }
    public void setChildrenCount(String childrenCount) { this.childrenCount = childrenCount; }

    public String getRoomPreference() { return roomPreference; }
    public void setRoomPreference(String roomPreference) { this.roomPreference = roomPreference; }

    public LocalDateTime getReservationDateTime() { return reservationDateTime; }
    public void setReservationDateTime(LocalDateTime reservationDateTime) { this.reservationDateTime = reservationDateTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
