package com.ibsec.ncdnotifier.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bond_maturity_notification_log")
public class BondMaturityNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;
    private String bondName;
    private String isin;
    private LocalDate maturityDate;
    private String message;
    private String channel;
    private String status;
    private LocalDateTime notifiedOn;

    // === Constructors ===
    public BondMaturityNotificationLog() {}

    public BondMaturityNotificationLog(Long id, String clientId, String bondName, String isin,
                                       LocalDate maturityDate, String message, String channel,
                                       String status, LocalDateTime notifiedOn) {
        this.id = id;
        this.clientId = clientId;
        this.bondName = bondName;
        this.isin = isin;
        this.maturityDate = maturityDate;
        this.message = message;
        this.channel = channel;
        this.status = status;
        this.notifiedOn = notifiedOn;
    }

    // === Getters and Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getBondName() { return bondName; }
    public void setBondName(String bondName) { this.bondName = bondName; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getNotifiedOn() { return notifiedOn; }
    public void setNotifiedOn(LocalDateTime notifiedOn) { this.notifiedOn = notifiedOn; }

    // === Manual Builder ===
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String clientId;
        private String bondName;
        private String isin;
        private LocalDate maturityDate;
        private String message;
        private String channel;
        private String status;
        private LocalDateTime notifiedOn;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder bondName(String bondName) {
            this.bondName = bondName;
            return this;
        }

        public Builder isin(String isin) {
            this.isin = isin;
            return this;
        }

        public Builder maturityDate(LocalDate maturityDate) {
            this.maturityDate = maturityDate;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder notifiedOn(LocalDateTime notifiedOn) {
            this.notifiedOn = notifiedOn;
            return this;
        }

        public BondMaturityNotificationLog build() {
            return new BondMaturityNotificationLog(
                    id, clientId, bondName, isin,
                    maturityDate, message, channel, status, notifiedOn
            );
        }
    }
}
