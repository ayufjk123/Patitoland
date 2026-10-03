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

    /** Origin of the booking: "WEB" (online form) or "SHEET" (Google Sheet sync). */
    private String source;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Deposit collected via POS, in cents. */
    private Integer depositAmountCents;

    /** PENDIENTE / COBRADO / DEVUELTO. */
    private String depositStatus = "PENDIENTE";

    /** Final payment collected via POS, in cents. */
    private Integer paidAmountCents;

    /** PENDIENTE / PAGADO. */
    private String paymentStatus = "PENDIENTE";

    private String posDepositOrderId;

    private String posPaymentOrderId;

    private LocalDateTime depositPaidAt;

    private LocalDateTime paidAt;

    /** Booking lifecycle: PENDIENTE / CONFIRMADA / COMPLETADA / CANCELADA. */
    private String status = "PENDIENTE";

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

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getDepositAmountCents() { return depositAmountCents; }
    public void setDepositAmountCents(Integer depositAmountCents) { this.depositAmountCents = depositAmountCents; }

    public String getDepositStatus() { return depositStatus; }
    public void setDepositStatus(String depositStatus) { this.depositStatus = depositStatus; }

    public Integer getPaidAmountCents() { return paidAmountCents; }
    public void setPaidAmountCents(Integer paidAmountCents) { this.paidAmountCents = paidAmountCents; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPosDepositOrderId() { return posDepositOrderId; }
    public void setPosDepositOrderId(String posDepositOrderId) { this.posDepositOrderId = posDepositOrderId; }

    public String getPosPaymentOrderId() { return posPaymentOrderId; }
    public void setPosPaymentOrderId(String posPaymentOrderId) { this.posPaymentOrderId = posPaymentOrderId; }

    public LocalDateTime getDepositPaidAt() { return depositPaidAt; }
    public void setDepositPaidAt(LocalDateTime depositPaidAt) { this.depositPaidAt = depositPaidAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
