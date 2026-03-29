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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests for RoomRepository.
 *
 * Uses @DataJpaTest with H2 in-memory — no live server, no Spring Security.
 *
 * Test targets:
 *   1. findAvailableRooms — overlap SQL (same logic as isRoomAvailable but <=/>= vs </>)
 *   2. searchRooms — multi-field LIKE query
 *
 * Seed booking: room 101 (SINGLE), 2030-06-10 ~ 2030-06-15, status=BOOKED
 * Seed room 2:  room 102 (DOUBLE), no booking
 */
@DataJpaTest
@DisplayName("📦 RoomRepository — findAvailableRooms & searchRooms SQL Tests")
class RoomRepositoryTest {

    @Autowired RoomRepository    roomRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired UserRepository    userRepository;

    private static final LocalDate EXISTING_CHECK_IN  = LocalDate.of(2030, 6, 10);
    private static final LocalDate EXISTING_CHECK_OUT = LocalDate.of(2030, 6, 15);

    private Room singleRoom;
    private Room doubleRoom;

    @BeforeEach
    void setUp() {
        singleRoom = roomRepository.save(Room.builder()
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("100.00"))
                .capacity(1)
                .description("Cozy single room")
                .build());

        doubleRoom = roomRepository.save(Room.builder()
                .roomNumber(102)
                .type(RoomType.DOUBLE)
                .pricePerNight(new BigDecimal("150.00"))
                .capacity(2)
                .description("Spacious double room")
                .build());

        User user = userRepository.save(User.builder()
                .email("repo_room_test@hotel.com")
                .password("encoded")
                .phoneNumber("09012345678")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.CUSTOMER)
                .build());

        bookingRepository.save(Booking.builder()
                .room(singleRoom)
                .user(user)
                .checkInDate(EXISTING_CHECK_IN)
                .checkOutDate(EXISTING_CHECK_OUT)
                .bookingStatus(BookingStatus.BOOKED)
                .paymentStatus(PaymentStatus.PENDING)
                .totalPrice(new BigDecimal("500.00"))
                .bookingReference("ROOMSEEDREF1")
                .build());
    }

    // ─────────────────────────────────────────────────────────────
    // TC-REPO-R-01 | 完全重叠 → 不返回该房间
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-R-01 | findAvailableRooms | 完全重叠日期 → 已预订房间不在结果中")
    void completeOverlap_bookedRoomExcluded() {
        List<Room> available = roomRepository.findAvailableRooms(
                LocalDate.of(2030, 6, 10),
                LocalDate.of(2030, 6, 15),
                RoomType.SINGLE);

        assertThat(available).doesNotContain(singleRoom);
    }

    // ─────────────────────────────────────────────────────────────
    // TC-REPO-R-02 | 同天换客（新 checkIn = 现有 checkOut）→ 验证当前行为
    //
    // findAvailableRooms 使用 <=/>= (与 isRoomAvailable 的 </> 不一致)
    // 预期：同天换客被阻塞 → singleRoom 不在结果中
    // ⚠️ 与 isRoomAvailable 行为不一致 — isRoomAvailable 允许同天换客（</>）
    //    但 findAvailableRooms 不允许（<=/>= ）
    //    建议统一 SQL 逻辑
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-R-02 | findAvailableRooms | 同天换客（新 checkIn = 现有 checkOut）→ 当前行为：阻塞（⚠️ 与 isRoomAvailable 不一致）")
    void boundary_newCheckInOnExistingCheckOut_currentlyBlocked() {
        List<Room> available = roomRepository.findAvailableRooms(
                LocalDate.of(2030, 6, 15),  // = existing checkOut
                LocalDate.of(2030, 6, 20),
                RoomType.SINGLE);

        // ⚠️ Bug: isRoomAvailable returns true for same-day handover (uses </>)
        //         but findAvailableRooms returns false (uses <=/>= ) — inconsistent
        System.out.println("⚠️  BUG TC-REPO-R-02: findAvailableRooms blocks same-day handover " +
                "(uses <=/>= ) but isRoomAvailable allows it (uses </>). " +
                "Fix: unify overlap SQL to use strict < and >.");
        assertThat(available).doesNotContain(singleRoom);  // documents current (broken) behavior
    }

    // ─────────────────────────────────────────────────────────────
    // TC-REPO-R-03 | CANCELLED 状态不阻塞 → 返回该房间
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-R-03 | findAvailableRooms | CANCELLED booking 不阻塞 → 房间出现在结果中")
    void cancelledBooking_doesNotBlockAvailability() {
        Booking existing = bookingRepository.findAll().get(0);
        existing.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(existing);

        List<Room> available = roomRepository.findAvailableRooms(
                EXISTING_CHECK_IN, EXISTING_CHECK_OUT, RoomType.SINGLE);

        assertThat(available).contains(singleRoom);
    }

    // ─────────────────────────────────────────────────────────────
    // TC-REPO-R-04 | roomType = null → 返回所有类型
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-R-04 | findAvailableRooms | roomType=null → 返回所有类型房间")
    void nullRoomType_returnsAllTypes() {
        List<Room> available = roomRepository.findAvailableRooms(
                LocalDate.of(2030, 7, 1),
                LocalDate.of(2030, 7, 5),
                null);  // no type filter

        assertThat(available).contains(singleRoom, doubleRoom);
    }

    // ─────────────────────────────────────────────────────────────
    // TC-REPO-R-05 | searchRooms — 按 type 关键词搜索
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-R-05 | searchRooms | 关键词 'single' → 只返回 SINGLE 类型房间")
    void searchRooms_byType_returnsSingleOnly() {
        List<Room> results = roomRepository.searchRooms("single");

        assertThat(results).contains(singleRoom);
        assertThat(results).doesNotContain(doubleRoom);
    }

    // ─────────────────────────────────────────────────────────────
    // TC-REPO-R-06 | searchRooms — 按 roomNumber 关键词搜索
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REPO-R-06 | searchRooms | 关键词 '101' → 按房间号匹配")
    void searchRooms_byRoomNumber_returnsCorrectRoom() {
        List<Room> results = roomRepository.searchRooms("101");

        assertThat(results).contains(singleRoom);
        assertThat(results).doesNotContain(doubleRoom);
    }
}
