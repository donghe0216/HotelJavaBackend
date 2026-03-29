package repository;

import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.BookingStatus;
import com.example.HotelBooking.enums.PaymentStatus;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.enums.UserRole;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.RoomRepository;
import com.example.HotelBooking.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests for BookingRepository.isRoomAvailable().
 *
 * Uses @DataJpaTest with H2 in-memory — no live server, no Spring Security.
 * Each test is wrapped in a transaction that rolls back automatically.
 *
 * Test target: the custom JPQL overlap condition:
 *   checkInDate <= b.checkOutDate AND checkOutDate >= b.checkInDate
 *   AND b.bookingStatus IN ('BOOKED', 'CHECKED_IN')
 *
 * Seed booking: room 101, 2030-06-10 ~ 2030-06-15, status=BOOKED
 */
@DataJpaTest
@DisplayName("📦 BookingRepository — isRoomAvailable SQL Tests")
class BookingRepositoryTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired RoomRepository    roomRepository;
    @Autowired UserRepository    userRepository;

    private static final LocalDate EXISTING_CHECK_IN  = LocalDate.of(2030, 6, 10);
    private static final LocalDate EXISTING_CHECK_OUT = LocalDate.of(2030, 6, 15);

    private Long roomId;

    @BeforeEach
    void setUp() {
        Room room = roomRepository.save(Room.builder()
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("100.00"))
                .capacity(2)
                .description("Test room")
                .build());
        roomId = room.getId();

        User user = userRepository.save(User.builder()
                .email("repo_test@hotel.com")
                .password("encoded")
                .phoneNumber("09012345678")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.CUSTOMER)
                .build());

        bookingRepository.save(Booking.builder()
                .room(room)
                .user(user)
                .checkInDate(EXISTING_CHECK_IN)
                .checkOutDate(EXISTING_CHECK_OUT)
                .bookingStatus(BookingStatus.BOOKED)
                .paymentStatus(PaymentStatus.PENDING)
                .totalPrice(new BigDecimal("500.00"))
                .bookingReference("SEEDREF001")
                .build());
    }

    // ─────────────────────────────────────────────────────────────
    // Not available → false
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-B-01 | 完全重叠（same dates）→ false")
    void completeOverlap_returnsNotAvailable() {
        boolean available = bookingRepository.isRoomAvailable(
                roomId,
                LocalDate.of(2030, 6, 10),
                LocalDate.of(2030, 6, 15));

        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("TC-REPO-B-02 | 部分重叠（start before, end during）→ false")
    void partialOverlap_startBefore_returnsNotAvailable() {
        boolean available = bookingRepository.isRoomAvailable(
                roomId,
                LocalDate.of(2030, 6, 8),   // before existing checkIn
                LocalDate.of(2030, 6, 12));  // during existing booking

        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("TC-REPO-B-03 | 部分重叠（start during, end after）→ false")
    void partialOverlap_endAfter_returnsNotAvailable() {
        boolean available = bookingRepository.isRoomAvailable(
                roomId,
                LocalDate.of(2030, 6, 12),  // during existing booking
                LocalDate.of(2030, 6, 18)); // after existing checkOut

        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("TC-REPO-B-04 | 边界：新 checkIn = 现有 checkOut（同天换客）→ true（允许当天换客）")
    void boundary_newCheckInOnExistingCheckOut_returnsAvailable() {
        // checkInDate(06-15) < b.checkOutDate(06-15) → FALSE → no overlap
        // Business rule: same-day handover is allowed.
        boolean available = bookingRepository.isRoomAvailable(
                roomId,
                LocalDate.of(2030, 6, 15),  // = existing checkOut
                LocalDate.of(2030, 6, 20));

        assertThat(available).isTrue();
    }

    // ─────────────────────────────────────────────────────────────
    // Available → true
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-B-05 | 完全不重叠（entirely before）→ true")
    void noOverlap_entirelyBefore_returnsAvailable() {
        boolean available = bookingRepository.isRoomAvailable(
                roomId,
                LocalDate.of(2030, 6, 1),
                LocalDate.of(2030, 6, 9));  // ends before existing checkIn

        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("TC-REPO-B-06 | 完全不重叠（entirely after）→ true")
    void noOverlap_entirelyAfter_returnsAvailable() {
        boolean available = bookingRepository.isRoomAvailable(
                roomId,
                LocalDate.of(2030, 6, 16),  // starts after existing checkOut
                LocalDate.of(2030, 6, 20));

        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("TC-REPO-B-07 | CANCELLED booking | 不阻塞相同日期的查询 → true")
    void cancelledBooking_doesNotBlockAvailability() {
        Booking existing = bookingRepository.findAll().get(0);
        existing.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(existing);

        boolean available = bookingRepository.isRoomAvailable(
                roomId, EXISTING_CHECK_IN, EXISTING_CHECK_OUT);

        assertThat(available).isTrue();
    }
}
