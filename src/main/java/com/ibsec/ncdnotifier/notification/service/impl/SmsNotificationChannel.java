package com.ibsec.ncdnotifier.notification.service.impl;


import com.ibsec.ncdnotifier.notification.request.NotificationRequest;
import com.ibsec.ncdnotifier.notification.request.NotificationResult;
import com.ibsec.ncdnotifier.notification.service.NotificationChannel;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;


@Service
public class SmsNotificationChannel implements NotificationChannel {

    @Value("${services.sms.base-url}")
    private String smsServiceUrl;

    @Value("${services.sms.auth}")
    private String smsAuthHeader;

    private final RestTemplate restTemplate = new RestTemplate();

    Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);


    @Override
    public NotificationResult send(NotificationRequest request) {
        // Create request body as expected by the .NET API
        Map<String, String> payload = new HashMap<>();
        payload.put("dest", request.getRecipient());
        payload.put("text", request.getMessage());
        payload.put("senderId", request.getSenderId());

        // Prepare headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", smsAuthHeader);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        // Make POST request
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    UriComponentsBuilder.fromHttpUrl(smsServiceUrl).toUriString(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info(" SMS sent successfully to :{}", request.getRecipient());
                return new NotificationResult(true, "SMS sent successfully");
            } else {
                log.info(" Failed to send SMS: {}", response.getStatusCode());
                return new NotificationResult(false, "Failed to send SMS");
            }
        } catch (Exception e) {
            log.info(" Error sending SMS: {}", e.getMessage());
            return new NotificationResult(false, e.getMessage());
        }
    }
}

