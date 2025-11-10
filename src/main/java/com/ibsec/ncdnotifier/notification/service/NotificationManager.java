package com.ibsec.ncdnotifier.notification.service;


import com.ibsec.ncdnotifier.notification.request.NotificationRequest;
import com.ibsec.ncdnotifier.notification.request.NotificationResult;
import com.ibsec.ncdnotifier.notification.request.NotificationType;

import com.ibsec.ncdnotifier.notification.service.impl.EmailNotificationChannel;
import com.ibsec.ncdnotifier.notification.service.impl.SmsNotificationChannel;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationManager {

    private final Map<NotificationType, NotificationChannel> channels;

    public NotificationManager(
            EmailNotificationChannel emailChannel,
            SmsNotificationChannel smsChannel) {
        Map<NotificationType, NotificationChannel> map = new HashMap<>();
        map.put(NotificationType.EMAIL, emailChannel);
        map.put(NotificationType.SMS, smsChannel);
        this.channels = Collections.unmodifiableMap(map);
    }

    public NotificationResult sendNotification(NotificationRequest request) {
        NotificationChannel channel = channels.get(request.getType());
        if (channel == null) {
            throw new IllegalArgumentException("Unsupported notification type: " + request.getType());
        }
        return channel.send(request);
    }
}
