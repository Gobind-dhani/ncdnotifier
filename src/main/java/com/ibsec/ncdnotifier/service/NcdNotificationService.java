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
    private int lookaheadDays;

    @Value("${ncd.notify-before-days}")
    private String notifyBeforeDays;

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

    public void processMaturingBonds() {
        int totalIsinsProcessed = 0;
        int isinsWithClients = 0;
        int isinsWithoutClients = 0;
        int skippedOutOfRange = 0;
        int skippedNullDate = 0;

        try {
            List<BondFileService.BondRecord> bonds = bondFileService.fetchAllBonds();
            LocalDate today = LocalDate.now();

            log.info("📄 Total bonds fetched from files: {}", bonds.size());

            for (BondFileService.BondRecord bond : bonds) {

                if (bond.maturityDate() == null) {
                    skippedNullDate++;
                    log.debug("⏭️ Skipping bond {} ({}): maturity date is null", bond.name(), bond.isin());
                    continue;
                }

                long daysToMaturity = Duration.between(today.atStartOfDay(), bond.maturityDate().atStartOfDay()).toDays();

                // Log the first few for inspection
                if (totalIsinsProcessed + skippedOutOfRange + skippedNullDate < 50) {
                    log.debug("Bond: {} ({}) matures in {} days", bond.name(), bond.isin(), daysToMaturity);
                }

                List<String> notifyDays = Arrays.asList(notifyBeforeDays.split(","));
                if (daysToMaturity <= lookaheadDays && daysToMaturity >= 0 &&
                        notifyDays.contains(String.valueOf(daysToMaturity))) {

                    totalIsinsProcessed++;

                    List<Map<String, Object>> clients = getClientsHoldingIsin(bond.isin());
                    if (clients.isEmpty()) isinsWithoutClients++;
                    else isinsWithClients++;

                    log.info("Found {} client(s) for ISIN {} ({})", clients.size(), bond.isin(), bond.name());

                    for (Map<String, Object> client : clients) {
                        String clientId = (String) client.get("client_id");
                        String partyCd = "C" + clientId;
                        Map<String, Object> cust = getCustomerDetails(partyCd);
                        if (cust == null) continue;

                        String email = (String) cust.get("email_id");
                        String mobile = (String) cust.get("mobile_no");

                        String message = String.format(
                                "Dear Client, your pledged NCD %s (%s) is reaching maturity in %d days. " +
                                        "Margin benefit will cease from %s. Positions may be squared off if margin insufficient.",
                                bond.name(), bond.isin(), daysToMaturity, bond.maturityDate()
                        );

                        sendAndLog(clientId, bond, message, email, mobile);
                    }

                } else {
                    skippedOutOfRange++;
                }
            }

            log.info("✅ Processing Summary:");
            log.info(" - Total bonds fetched: {}", bonds.size());
            log.info(" - Bonds skipped (null maturity): {}", skippedNullDate);
            log.info(" - Bonds skipped (outside lookahead or notify days): {}", skippedOutOfRange);
            log.info(" - ISINs processed: {}", totalIsinsProcessed);
            log.info(" - ISINs with clients: {}", isinsWithClients);
            log.info(" - ISINs without clients: {}", isinsWithoutClients);

        } catch (Exception e) {
            log.error("Error processing maturing bonds", e);
        }
    }

    private List<Map<String, Object>> getClientsHoldingIsin(String isin) {
        String sql = "SELECT DISTINCT client_id FROM sapphire.vw_final_holding_midoffice WHERE isin = ?";
        List<Map<String, Object>> clients = primaryJdbcTemplate.queryForList(sql, isin);

        // ✅ Log each ISIN lookup and how many clients were found
        log.info("Queried ISIN {} -> {} client(s) found in vw_final_holding_midoffice", isin, clients.size());

        return clients;
    }

    private Map<String, Object> getCustomerDetails(String partyCd) {
        String sql = "SELECT email_id, mobile_no FROM focus.cust_mst WHERE party_cd = ?";
        List<Map<String, Object>> result = secondaryJdbcTemplate.queryForList(sql, partyCd);
        return result.isEmpty() ? null : result.get(0);
    }

    private void sendAndLog(String clientId, BondFileService.BondRecord bond, String message, String email, String mobile) {

        LocalDate maturityDate = bond.maturityDate() != null ? bond.maturityDate() : LocalDate.now();

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
        }

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
        }
    }
}
