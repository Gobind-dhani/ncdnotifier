package com.ibsec.ncdnotifier.notification.service.impl;

import com.ibsec.ncdnotifier.notification.request.NotificationRequest;
import com.ibsec.ncdnotifier.notification.request.NotificationResult;
import com.ibsec.ncdnotifier.notification.service.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

@Service
public class EmailNotificationChannel implements NotificationChannel {

    @Value("${netcore.url}")
    private String netcoreUrl;

    @Value("${netcore.port}")
    private String netcorePort;

    @Value("${netcore.smtp.userid}")
    private String netcoreUserId;

    @Value("${netcore.smtp.password}")
    private String netcorePassword;

    @Value("${netcore.from.mail.id}")
    private String netcoreFromMailId;

    private static final String INDIABULLS_SECURITIES = "Indiabulls Securities";

    Logger logger = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Override
    public NotificationResult send(NotificationRequest request) {
        try {
            Session session = setNetcoreProperties();

            MimeMessage message = new MimeMessage(session);
            logger.info("from mail id :: {} to mail id :: {}", netcoreFromMailId, request.getRecipient());
            InternetAddress addressFrom = new InternetAddress(netcoreFromMailId, INDIABULLS_SECURITIES);
            message.setFrom(addressFrom);
            message.setSender(addressFrom);

            message.addRecipient(Message.RecipientType.TO, new InternetAddress(request.getRecipient()));
            message.setSubject(request.getSubject());
            message.setContent(request.getMessage(), request.getContentType());

            Transport transport = session.getTransport();
            transport.connect();
            Transport.send(message);
            transport.close();
            logger.info("HTML email sent successfully...");
            return new NotificationResult(true, "Email sent successfully");
        } catch (Exception e) {
            logger.error("Exception occurred while sending HTML email", e);
            return new NotificationResult(false, e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    private Session setNetcoreProperties() {
        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", netcoreUrl);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", netcoreUrl);
        props.put("mail.smtp.port", netcorePort);

        logger.info("Using Netcore SMTP -> host: {}, port: {}, userId: {}", netcoreUrl, netcorePort, netcoreUserId);

        return Session.getDefaultInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(netcoreUserId, netcorePassword);
            }
        });
    }
}
