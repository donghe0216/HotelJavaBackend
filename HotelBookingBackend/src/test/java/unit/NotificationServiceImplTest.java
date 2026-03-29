package unit;

import com.example.HotelBooking.dtos.NotificationDTO;
import com.example.HotelBooking.entities.Notification;
import com.example.HotelBooking.enums.NotificationType;
import com.example.HotelBooking.repositories.NotificationRepository;
import com.example.HotelBooking.services.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl Unit Tests")
class NotificationServiceImplTest {

    @Mock private JavaMailSender         javaMailSender;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private NotificationDTO dto;

    @BeforeEach
    void setUp() {
        dto = new NotificationDTO();
        dto.setRecipient("customer@hotel.com");
        dto.setSubject("Booking Confirmation");
        dto.setBody("Your booking is confirmed.");
        dto.setBookingReference("ABC-123");
    }

    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-NS-01 | sendEmail | SMTP succeeds → notification record saved")
    void sendEmail_smtpSucceeds_notificationSaved() {
        notificationService.sendEmail(dto);

        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("TC-NS-02 | sendEmail | SMTP fails → notification record still saved (bug fix verified)")
    void sendEmail_smtpFails_notificationStillSaved() {
        // Simulate SMTP server down
        doThrow(new MailSendException("SMTP connection refused"))
                .when(javaMailSender).send(any(SimpleMailMessage.class));

        // Must not throw — exception is swallowed inside try-catch
        notificationService.sendEmail(dto);

        // Notification record must still be persisted despite SMTP failure
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("TC-NS-03 | sendEmail | saved notification has type=EMAIL and correct fields")
    void sendEmail_savedNotification_hasCorrectFields() {
        notificationService.sendEmail(dto);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.EMAIL);
        assertThat(saved.getRecipient()).isEqualTo("customer@hotel.com");
        assertThat(saved.getSubject()).isEqualTo("Booking Confirmation");
        assertThat(saved.getBookingReference()).isEqualTo("ABC-123");
    }

    @Test
    @DisplayName("TC-NS-04 | sendEmail | SMTP failure → email sent with correct recipient and subject")
    void sendEmail_smtpCall_usesCorrectRecipientAndSubject() {
        notificationService.sendEmail(dto);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("customer@hotel.com");
        assertThat(sent.getSubject()).isEqualTo("Booking Confirmation");
        assertThat(sent.getText()).isEqualTo("Your booking is confirmed.");
    }
}
