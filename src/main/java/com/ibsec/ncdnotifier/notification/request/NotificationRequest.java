package com.ibsec.ncdnotifier.notification.request;

public class NotificationRequest {

    private String recipient;      // e.g., phone number or email
    private String message;        // for SMS 'text'
    private String subject;        // for emails
    private NotificationType type; // EMAIL or SMS
    private String senderId;       // used for SMS (IBSECL, etc.) or email sender address
    private String contentType;    // "text/html" or "text/plain"

    // ======= Constructors =======
    public NotificationRequest() {}

    public NotificationRequest(String recipient, String message, String subject,
                               NotificationType type, String senderId, String contentType) {
        this.recipient = recipient;
        this.message = message;
        this.subject = subject;
        this.type = type;
        this.senderId = senderId;
        this.contentType = contentType;
    }

    // ======= Getters and Setters =======
    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    // ======= Manual Builder =======
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String recipient;
        private String message;
        private String subject;
        private NotificationType type;
        private String senderId;
        private String contentType;

        public Builder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder type(NotificationType type) {
            this.type = type;
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public NotificationRequest build() {
            return new NotificationRequest(recipient, message, subject, type, senderId, contentType);
        }
    }
}
