package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.NotificationDTO;
import com.example.HotelBooking.entities.Notification;
import com.example.HotelBooking.enums.NotificationType;
import com.example.HotelBooking.repositories.NotificationRepository;
import com.example.HotelBooking.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {


    private final JavaMailSender javaMailSender;

    private final NotificationRepository notificationRepository;

    @Override
    @Async
    public void sendEmail(NotificationDTO notificationDTO) {
        log.info("Sending email to {}", notificationDTO.getRecipient());

        // Attempt SMTP delivery — failure must not prevent the notification record from being saved
        try {
            SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
            simpleMailMessage.setTo(notificationDTO.getRecipient());
            simpleMailMessage.setSubject(notificationDTO.getSubject());
            simpleMailMessage.setText(notificationDTO.getBody());
            javaMailSender.send(simpleMailMessage);
        } catch (Exception ex) {
            log.warn("Failed to send email to {}: {}", notificationDTO.getRecipient(), ex.getMessage());
        }

        // Always persist the notification record regardless of SMTP outcome
        Notification notificationToSave = Notification.builder()
                .recipient(notificationDTO.getRecipient())
                .subject(notificationDTO.getSubject())
                .body(notificationDTO.getBody())
                .bookingReference(notificationDTO.getBookingReference())
                .type(NotificationType.EMAIL)
                .build();

        notificationRepository.save(notificationToSave);
    }

    @Override
    public void sendSms() {
        // Not yet implemented — placeholder for future SMS integration
    }

    @Override
    public void sendWhatsapp() {
        // Not yet implemented — placeholder for future WhatsApp integration
    }
}
