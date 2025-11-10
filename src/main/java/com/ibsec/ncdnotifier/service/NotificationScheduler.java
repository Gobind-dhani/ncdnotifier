package com.ibsec.ncdnotifier.service;


import com.ibsec.ncdnotifier.notification.service.impl.EmailNotificationChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component

public class NotificationScheduler {

    Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NcdNotificationService ncdNotificationService;

    public NotificationScheduler(NcdNotificationService ncdNotificationService) {
        this.ncdNotificationService = ncdNotificationService;
    }

    @Scheduled(cron = "0 0/1 * * * *")
    public void runDaily() {
        log.info("Starting NCD maturity notification job...");
        ncdNotificationService.processMaturingBonds();
        log.info("NCD maturity notification job completed.");
    }
}
