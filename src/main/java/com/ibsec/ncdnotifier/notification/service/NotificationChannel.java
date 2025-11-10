package com.ibsec.ncdnotifier.notification.service;




import com.ibsec.ncdnotifier.notification.request.NotificationRequest;
import com.ibsec.ncdnotifier.notification.request.NotificationResult;

public interface NotificationChannel {
    NotificationResult send(NotificationRequest request);
}