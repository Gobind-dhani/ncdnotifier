package com.ibsec.ncdnotifier.service;

import com.ibsec.ncdnotifier.entity.BondMaturityNotificationLog;
import com.ibsec.ncdnotifier.notification.request.NotificationRequest;
import com.ibsec.ncdnotifier.notification.request.NotificationType;
import com.ibsec.ncdnotifier.notification.service.NotificationManager;
import com.ibsec.ncdnotifier.repository.BondMaturityNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class NcdNotificationService {

    private final BondFileService bondFileService;
    private final BondMaturityNotificationRepository logRepo;
    private final JdbcTemplate secondaryJdbcTemplate;
    private final JdbcTemplate primaryJdbcTemplate;
    private final NotificationManager notificationManager;

    @Value("${ncd.maturity-lookahead-days}")
    private int lookaheadDays; // Example: 180 days

    @Value("${ncd.notify-before-days}")
    private String notifyBeforeDays; // Example: 30,15,7,3,2,1

    private static final Logger log = LoggerFactory.getLogger(NcdNotificationService.class);

    public NcdNotificationService(
            BondFileService bondFileService,
            BondMaturityNotificationRepository logRepo,
            @Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
            @Qualifier("secondaryJdbcTemplate") JdbcTemplate secondaryJdbcTemplate,
            NotificationManager notificationManager) {

        this.bondFileService = bondFileService;
        this.logRepo = logRepo;
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.secondaryJdbcTemplate = secondaryJdbcTemplate;
        this.notificationManager = notificationManager;
    }

    /**
     * MAIN ENTRY
     */
    public void processMaturingBonds() {

        int totalValidIsins = 0;
        int totalNotificationsSent = 0;
        int skippedNullDate = 0;
        int skippedOutOfWindow = 0;
        int isinsWithClients = 0;
        int isinsWithoutClients = 0;

        try {
            List<BondFileService.BondRecord> bonds = bondFileService.fetchAllBonds();
            LocalDate today = LocalDate.now();
            List<String> notifyDaysList = Arrays.asList(notifyBeforeDays.split(","));

            log.info("📄 Total bonds fetched from files: {}", bonds.size());

            for (BondFileService.BondRecord bond : bonds) {

                if (bond.maturityDate() == null) {
                    skippedNullDate++;
                    continue;
                }

                long daysLeft = Duration.between(
                        today.atStartOfDay(),
                        bond.maturityDate().atStartOfDay()
                ).toDays();

                // 👇 FIXED LOGIC:
                // Step 1: Include ALL ISINs within lookahead window (0 → 180 days)
                if (daysLeft < 0 || daysLeft > lookaheadDays) {
                    skippedOutOfWindow++;
                    continue;
                }

                totalValidIsins++;

                // Step 2: Only send notifications on notify-before-days
                boolean shouldNotifyToday = notifyDaysList.contains(String.valueOf(daysLeft));

                if (!shouldNotifyToday) {
                    continue; // valid isin but no notification today
                }

                // Fetch clients holding the ISIN
                List<Map<String, Object>> clients = getClientsHoldingIsin(bond.isin());

                if (clients.isEmpty()) {
                    isinsWithoutClients++;
                    continue;
                } else {
                    isinsWithClients++;
                }

                log.info("🔔 ISIN {} ({}) matures in {} days → {} clients found",
                        bond.isin(), bond.name(), daysLeft, clients.size());

                // Send notifications to all clients
                for (Map<String, Object> client : clients) {

                    String clientId = (String) client.get("client_id");
                    String partyCd = "C" + clientId;

                    Map<String, Object> cust = getCustomerDetails(partyCd);
                    if (cust == null) {
                        continue;
                    }

                    String email = (String) cust.get("email_id");
                    String mobile = (String) cust.get("mobile_no");

                    String message = String.format(
                            "Dear Client, your pledged NCD %s (%s) is reaching maturity in %d days. " +
                                    "Margin benefit will cease from %s. Positions may be squared off if margin is insufficient.",
                            bond.name(), bond.isin(), daysLeft, bond.maturityDate()
                    );

                    totalNotificationsSent += sendAndLog(clientId, bond, message, email, mobile);
                }
            }

            // ─────────────────────────────────────────────
            // FINAL SUMMARY
            // ─────────────────────────────────────────────
            log.info(" NCD NOTIFICATION SUMMARY");
            log.info("• Total bonds fetched: {}", bonds.size());
            log.info("• Bonds skipped (null maturity): {}", skippedNullDate);
            log.info("• Bonds outside lookahead window: {}", skippedOutOfWindow);
            log.info("• Valid ISINs in lookahead window: {}", totalValidIsins);
            log.info("• ISINs with clients: {}", isinsWithClients);
            log.info("• ISINs without clients: {}", isinsWithoutClients);
            log.info("• Total notifications sent: {}", totalNotificationsSent);

        } catch (Exception e) {
            log.error(" Error processing maturing bonds", e);
        }
    }

    // ============================================================
    // DB METHODS
    // ============================================================

    private List<Map<String, Object>> getClientsHoldingIsin(String isin) {
        String sql = "SELECT DISTINCT client_id FROM sapphire.vw_final_holding_midoffice WHERE isin = ?";
        List<Map<String, Object>> list = primaryJdbcTemplate.queryForList(sql, isin);

        log.debug("ISIN {} → {} clients found", isin, list.size());
        return list;
    }

    private Map<String, Object> getCustomerDetails(String partyCd) {
        String sql = "SELECT email_id, mobile_no FROM focus.cust_mst WHERE party_cd = ?";
        List<Map<String, Object>> result = secondaryJdbcTemplate.queryForList(sql, partyCd);
        return result.isEmpty() ? null : result.get(0);
    }

    // ============================================================
    // SEND NOTIFICATION + LOG
    // ============================================================

    private int sendAndLog(String clientId, BondFileService.BondRecord bond,
                           String message, String email, String mobile) {

        int sentCount = 0;
        LocalDate maturityDate = bond.maturityDate();

        // ---------- EMAIL ----------
        if (email != null && !email.isBlank()) {

            notificationManager.sendNotification(NotificationRequest.builder()
                    .recipient(email)
                    .subject("NCD Maturity Alert")
                    .message(message)
                    .senderId("noreply@indiabulls.com")
                    .type(NotificationType.EMAIL)
                    .contentType("text/plain")
                    .build());

            logRepo.save(BondMaturityNotificationLog.builder()
                    .clientId(clientId)
                    .bondName(bond.name())
                    .isin(bond.isin())
                    .maturityDate(maturityDate)
                    .message(message)
                    .channel("EMAIL")
                    .status("SENT")
                    .notifiedOn(LocalDateTime.now())
                    .build());

            sentCount++;
        }

        // ---------- SMS ----------
        if (mobile != null && !mobile.isBlank()) {

            notificationManager.sendNotification(NotificationRequest.builder()
                    .recipient(mobile)
                    .message(message)
                    .senderId("IBSECL")
                    .type(NotificationType.SMS)
                    .build());

            logRepo.save(BondMaturityNotificationLog.builder()
                    .clientId(clientId)
                    .bondName(bond.name())
                    .isin(bond.isin())
                    .maturityDate(maturityDate)
                    .message(message)
                    .channel("SMS")
                    .status("SENT")
                    .notifiedOn(LocalDateTime.now())
                    .build());

            sentCount++;
        }

        return sentCount;
    }
}
