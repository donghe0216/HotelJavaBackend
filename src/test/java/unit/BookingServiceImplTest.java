package unit;

import com.example.HotelBooking.dtos.BookingDTO;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.BookingStatus;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.enums.UserRole;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.RoomRepository;
import com.example.HotelBooking.services.BookingCodeGenerator;
import com.example.HotelBooking.services.NotificationService;
import com.example.HotelBooking.services.UserService;
import com.example.HotelBooking.services.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookingServiceImpl}.
 *
 * <p>Covers: date validation, total-price calculation, happy-path booking creation,
 * partial-update behaviour, and null-input guards.
 *
 * <p>All external dependencies (repositories, services) are mocked via Mockito;
 * no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository    bookingRepository;
    @Mock private RoomRepository       roomRepository;
    @Mock private NotificationService  notificationService;
    @Mock private ModelMapper          modelMapper;
    @Mock private UserService          userService;
    @Mock private BookingCodeGenerator bookingCodeGenerator;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Room        testRoom;
    private User        testUser;
    private BookingDTO  validBookingDTO;

    @BeforeEach
    void setUp() {
        testRoom = Room.builder()
                .id(1L)
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("100.00"))
                .capacity(1)
                .build();

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("customer@hotel.com");
        testUser.setRole(UserRole.CUSTOMER);

        validBookingDTO = new BookingDTO();
        validBookingDTO.setRoomId(testRoom.getId());
        validBookingDTO.setCheckInDate(LocalDate.now().plusDays(1));
        validBookingDTO.setCheckOutDate(LocalDate.now().plusDays(3));
    }

    @Test
    @DisplayName("TC-BS-01 | createBooking | checkOut < checkIn → throws")
    void checkOutBeforeCheckIn_shouldThrow() {
        validBookingDTO.setCheckInDate(LocalDate.now().plusDays(5));
        validBookingDTO.setCheckOutDate(LocalDate.now().plusDays(3));

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("check out date cannot be before check in date");
    }

    @Test
    @DisplayName("TC-BS-02 | createBooking | checkIn == checkOut → throws")
    void checkInSameAsCheckOut_shouldThrow() {
        LocalDate same = LocalDate.now().plusDays(2);
        validBookingDTO.setCheckInDate(same);
        validBookingDTO.setCheckOutDate(same);

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("check in date cannot be equal to check out date");
    }

    @Test
    @DisplayName("TC-BS-03 | createBooking | checkIn in the past → throws")
    void checkInBeforeToday_shouldThrow() {
        validBookingDTO.setCheckInDate(LocalDate.now().minusDays(1));
        validBookingDTO.setCheckOutDate(LocalDate.now().plusDays(1));

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("check in date cannot be before today");
    }

    @Test
    @DisplayName("TC-BS-04 | createBooking | checkIn = today → valid, does not throw")
    void checkInToday_shouldNotThrow() {
        // Boundary: checkIn == today is the earliest allowed date (isBefore(today) is false)
        validBookingDTO.setCheckInDate(LocalDate.now());
        validBookingDTO.setCheckOutDate(LocalDate.now().plusDays(1));

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(bookingRepository.isRoomAvailable(any(), any(), any())).thenReturn(true);
        when(bookingCodeGenerator.generateBookingReference()).thenReturn("ABCDEFGHIJ");
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(modelMapper.map(any(Booking.class), eq(BookingDTO.class))).thenAnswer(inv -> new BookingDTO());

        var response = bookingService.createBooking(validBookingDTO);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-BS-05 | createBooking | room not available → throws")
    void roomNotAvailable_shouldThrow() {
        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(bookingRepository.isRoomAvailable(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("TC-BS-06 | createBooking | roomId not found → throws, no save")

    void roomNotFound_shouldThrow() {
        validBookingDTO.setRoomId(999L);
        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(NotFoundException.class)
                .hasMessageMatching("(?i).*not.?found.*");

        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} nights × ${1}/night = ${2}")
    @CsvSource({
        "1,  100.00, 100.00",
        "2,  100.00, 200.00",
        "30, 100.00, 3000.00",
        "3,   99.99, 299.97",
    })
    @DisplayName("TC-BS-07 | createBooking | totalPrice = nights × pricePerNight")
    void totalPrice_shouldEqual_nightsTimesPrice(int nights, String pricePerNight, String expectedTotal) {
        testRoom.setPricePerNight(new BigDecimal(pricePerNight));
        validBookingDTO.setCheckInDate(LocalDate.now().plusDays(1));
        validBookingDTO.setCheckOutDate(LocalDate.now().plusDays(1 + nights));

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(bookingRepository.isRoomAvailable(any(), any(), any())).thenReturn(true);
        when(bookingCodeGenerator.generateBookingReference()).thenReturn("ABCDEFGHIJ");
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(modelMapper.map(any(Booking.class), eq(BookingDTO.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            BookingDTO dto = new BookingDTO();
            dto.setTotalPrice(b.getTotalPrice());
            return dto;
        });

        var response = bookingService.createBooking(validBookingDTO);

        assertThat(response.getBooking().getTotalPrice())
                .isEqualByComparingTo(new BigDecimal(expectedTotal));
    }

    @Test
    @DisplayName("TC-BS-08 | createBooking | valid input → saved with BOOKED status, sends email")
    void validBooking_shouldSaveAndReturn200() {
        validBookingDTO.setCheckInDate(LocalDate.now().plusDays(1));
        validBookingDTO.setCheckOutDate(LocalDate.now().plusDays(2));

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(bookingRepository.isRoomAvailable(any(), any(), any())).thenReturn(true);
        when(bookingCodeGenerator.generateBookingReference()).thenReturn("ABCDEFGHIJ");
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(modelMapper.map(any(Booking.class), eq(BookingDTO.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            BookingDTO dto = new BookingDTO();
            dto.setBookingStatus(b.getBookingStatus());
            dto.setBookingReference(b.getBookingReference());
            dto.setTotalPrice(b.getTotalPrice());
            dto.setCheckInDate(b.getCheckInDate());
            dto.setCheckOutDate(b.getCheckOutDate());
            return dto;
        });

        var response = bookingService.createBooking(validBookingDTO);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(notificationService, times(1)).sendEmail(any());

        BookingDTO result = response.getBooking();
        assertThat(result.getBookingStatus()).isEqualTo(BookingStatus.BOOKED);
        assertThat(result.getBookingReference()).isNotNull();
        assertThat(result.getBookingReference()).matches("[A-Z1-9]{10}");
        assertThat(result.getTotalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("TC-BS-09 | updateBooking | id=null → throws, no save")
    void updateBooking_nullId_throwsNotFoundException() {
        BookingDTO dto = new BookingDTO();
        dto.setId(null);
        dto.setBookingStatus(BookingStatus.CANCELLED);

        assertThatThrownBy(() -> bookingService.updateBooking(dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("id");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-BS-10 | updateBooking | id not found → throws, no save")
    void updateBooking_idNotFound_throwsNotFoundException() {
        BookingDTO dto = new BookingDTO();
        dto.setId(999L);
        dto.setBookingStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.updateBooking(dto))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Not Found");

        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("validTransitions")
    @DisplayName("TC-BS-11 | updateBooking | valid transitions (4 cases) → 200 + status persisted")
    void validStatusTransition_shouldSaveAndReturn200(BookingStatus from, BookingStatus to) {
        Booking existing = new Booking();
        existing.setId(1L);
        existing.setBookingStatus(from);

        BookingDTO dto = new BookingDTO();
        dto.setId(1L);
        dto.setBookingStatus(to);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Response response = bookingService.updateBooking(dto);

        assertThat(response.getStatus()).isEqualTo(200);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(to);
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
            // Guest arrives and checks in at the front desk
            Arguments.of(BookingStatus.BOOKED,     BookingStatus.CHECKED_IN),
            // Booking cancelled before guest arrives
            Arguments.of(BookingStatus.BOOKED,     BookingStatus.CANCELLED),
            // Guest did not arrive — room was held but never used
            Arguments.of(BookingStatus.BOOKED,     BookingStatus.NO_SHOW),
            // Guest completes their stay and checks out
            Arguments.of(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT)
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("invalidTransitions")
    @DisplayName("TC-BS-12 | updateBooking | invalid transitions (11 cases) → InvalidBookingStateAndDateException, no save")
    void invalidStatusTransition_shouldThrow(BookingStatus from, BookingStatus to) {
        Booking existing = new Booking();
        existing.setId(1L);
        existing.setBookingStatus(from);

        BookingDTO dto = new BookingDTO();
        dto.setId(1L);
        dto.setBookingStatus(to);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> bookingService.updateBooking(dto))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("Invalid status transition");

        verify(bookingRepository, never()).save(any());
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
            // Terminal state: CHECKED_OUT
            Arguments.of(BookingStatus.CHECKED_OUT, BookingStatus.BOOKED),
            Arguments.of(BookingStatus.CHECKED_OUT, BookingStatus.CHECKED_IN),
            Arguments.of(BookingStatus.CHECKED_OUT, BookingStatus.CANCELLED),
            // Terminal state: CANCELLED
            Arguments.of(BookingStatus.CANCELLED,   BookingStatus.BOOKED),
            Arguments.of(BookingStatus.CANCELLED,   BookingStatus.CHECKED_IN),
            Arguments.of(BookingStatus.CANCELLED,   BookingStatus.CHECKED_OUT),
            // Terminal state: NO_SHOW
            Arguments.of(BookingStatus.NO_SHOW,     BookingStatus.BOOKED),
            Arguments.of(BookingStatus.NO_SHOW,     BookingStatus.CHECKED_IN),
            Arguments.of(BookingStatus.NO_SHOW,     BookingStatus.CHECKED_OUT),
            // Skip transition: BOOKED → CHECKED_OUT (bypasses CHECKED_IN)
            Arguments.of(BookingStatus.BOOKED,      BookingStatus.CHECKED_OUT),
            // Revert: CHECKED_IN → BOOKED
            Arguments.of(BookingStatus.CHECKED_IN,  BookingStatus.BOOKED)
        );
    }

    @Test
    @DisplayName("TC-BS-13 | createBooking | roomId=null → throws, no save")
    void should_throw_when_roomId_is_null() {
        validBookingDTO.setRoomId(null);
        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("roomId is required");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-BS-14 | createBooking | pricePerNight=null → throws domain exception, no save")
    void should_throw_when_price_is_null() {
        testRoom.setPricePerNight(null);

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(bookingRepository.isRoomAvailable(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("price");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-BS-15 | createBooking | checkInDate=null → throws")
    void should_throw_when_checkInDate_is_null() {
        validBookingDTO.setCheckInDate(null);

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("required");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-BS-16 | createBooking | checkOutDate=null → throws")
    void should_throw_when_checkOutDate_is_null() {
        validBookingDTO.setCheckOutDate(null);

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("required");

        verify(bookingRepository, never()).save(any());
    }

    // ── cancelBooking date-boundary tests ─────────────────────────────────────
    // Policy: cancellation must be made more than 24 hours before check-in.
    // In calendar-date terms: checkInDate must be at least the day after tomorrow.
    // Boundary points: tomorrow (last invalid) and day-after-tomorrow (first valid).

    @ParameterizedTest(name = "checkInDate = {0} → within 24h window, must throw")
    @MethodSource("cancelTooLateInputs")
    @DisplayName("TC-BS-CANCEL-01~02 | cancelBooking | checkInDate within 24h → throws")
    void cancelBooking_checkInWithin24Hours_shouldThrow(LocalDate checkInDate) {
        Booking booking = buildCancellableBooking(checkInDate);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);

        assertThatThrownBy(() -> bookingService.cancelBooking(1L))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("24 hours");
    }

    static Stream<Arguments> cancelTooLateInputs() {
        return Stream.of(
            // TC-BS-CANCEL-01: check-in is today — 0 days away
            Arguments.of(LocalDate.now()),
            // TC-BS-CANCEL-02: check-in is tomorrow — boundary, last invalid value
            Arguments.of(LocalDate.now().plusDays(1))
        );
    }

    @Test
    @DisplayName("TC-BS-CANCEL-03 | cancelBooking | checkInDate = day after tomorrow → succeeds (valid boundary)")
    void cancelBooking_checkInDayAfterTomorrow_shouldSucceed() {
        Booking booking = buildCancellableBooking(LocalDate.now().plusDays(2));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(1L);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    /** Builds a BOOKED booking owned by testUser with the given check-in date. */
    private Booking buildCancellableBooking(LocalDate checkInDate) {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setUser(testUser);
        booking.setBookingStatus(BookingStatus.BOOKED);
        booking.setCheckInDate(checkInDate);
        booking.setCheckOutDate(checkInDate.plusDays(2));
        return booking;
    }

}
