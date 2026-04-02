package unit;

import com.example.HotelBooking.dtos.BookingDTO;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.BookingStatus;
import com.example.HotelBooking.enums.PaymentStatus;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.RoomType;
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
import org.junit.jupiter.params.provider.CsvSource;
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
 * reference-number lookup, partial-update behaviour, and null-input guards.
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
    @DisplayName("TC-BS-04 | createBooking | room not available → throws")
    void roomNotAvailable_shouldThrow() {
        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(bookingRepository.isRoomAvailable(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("TC-BS-05 | createBooking | roomId not found → throws, no save")
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
    @DisplayName("TC-BS-06 | createBooking | totalPrice = nights × pricePerNight")
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
    @DisplayName("TC-BS-07 | createBooking | valid input → saved with BOOKED/PENDING, sends email")
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
            dto.setPaymentStatus(b.getPaymentStatus());
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
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getBookingReference()).isNotNull();
        assertThat(result.getBookingReference()).matches("[A-Z1-9]{10}");
        assertThat(result.getTotalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("TC-BS-08 | findBookingByReferenceNo | valid reference → returns full BookingDTO")
    void getBookingByReference_validRef() {
        Booking booking = new Booking();
        booking.setBookingReference("ABCDEFGHIJ");
        booking.setBookingStatus(BookingStatus.BOOKED);
        booking.setPaymentStatus(PaymentStatus.PENDING);
        booking.setCheckInDate(LocalDate.now().plusDays(1));
        booking.setCheckOutDate(LocalDate.now().plusDays(3));
        booking.setTotalPrice(new BigDecimal("200.00"));

        when(bookingRepository.findByBookingReference("ABCDEFGHIJ")).thenReturn(Optional.of(booking));
        when(modelMapper.map(any(Booking.class), eq(BookingDTO.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            BookingDTO dto = new BookingDTO();
            dto.setBookingReference(b.getBookingReference());
            dto.setBookingStatus(b.getBookingStatus());
            dto.setPaymentStatus(b.getPaymentStatus());
            dto.setTotalPrice(b.getTotalPrice());
            dto.setCheckInDate(b.getCheckInDate());
            dto.setCheckOutDate(b.getCheckOutDate());
            return dto;
        });

        Response response = bookingService.findBookingByReferenceNo("ABCDEFGHIJ");

        assertThat(response.getStatus()).isEqualTo(200);
        BookingDTO result = response.getBooking();
        assertThat(result.getBookingReference()).isEqualTo("ABCDEFGHIJ");
        assertThat(result.getBookingStatus()).isNotNull();
        assertThat(result.getPaymentStatus()).isNotNull();
        assertThat(result.getTotalPrice()).isNotNull();
        assertThat(result.getCheckInDate()).isNotNull();
        assertThat(result.getCheckOutDate()).isNotNull();
    }

    @Test
    @DisplayName("TC-BS-09 | findBookingByReferenceNo | unknown reference → throws")
    void getBookingByReference_notFound() {
        when(bookingRepository.findByBookingReference("INVALID000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findBookingByReferenceNo("INVALID000"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageMatching("(?i).*not.?found.*");
    }

    @Test
    @DisplayName("TC-BS-10 | updateBooking | id=null → throws, no save")
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
    @DisplayName("TC-BS-11 | updateBooking | id not found → throws, no save")
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

    @Test
    @DisplayName("TC-BS-12 | updateBooking | bookingStatus only — paymentStatus unchanged")
    void updateBooking_onlyBookingStatus_paymentStatusUnchanged() {
        Booking existing = new Booking();
        existing.setId(1L);
        existing.setBookingStatus(BookingStatus.BOOKED);
        existing.setPaymentStatus(PaymentStatus.COMPLETED);

        BookingDTO dto = new BookingDTO();
        dto.setId(1L);
        dto.setBookingStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Response response = bookingService.updateBooking(dto);

        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("TC-BS-13 | updateBooking | paymentStatus only — bookingStatus unchanged")
    void updateBooking_onlyPaymentStatus_bookingStatusUnchanged() {
        Booking existing = new Booking();
        existing.setId(1L);
        existing.setBookingStatus(BookingStatus.CHECKED_IN);
        existing.setPaymentStatus(PaymentStatus.PENDING);

        BookingDTO dto = new BookingDTO();
        dto.setId(1L);
        dto.setPaymentStatus(PaymentStatus.COMPLETED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Response response = bookingService.updateBooking(dto);

        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("TC-BS-14 | createBooking | roomId=null → throws, no save")
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
    @DisplayName("TC-BS-15 | createBooking | [Bug] pricePerNight=null → NPE, no domain exception")
    void should_throw_when_price_is_null() {
        // Bug: calculateTotalPrice calls pricePerNight.multiply() without a null guard.
        // If room data is incomplete, an NPE is thrown instead of a domain exception.
        // Fix: validate pricePerNight != null in addRoom() or at the start of calculateTotalPrice().
        testRoom.setPricePerNight(null);

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));
        when(bookingRepository.isRoomAvailable(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(NullPointerException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-BS-16 | createBooking | checkInDate=null → throws")
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
    @DisplayName("TC-BS-17 | createBooking | checkOutDate=null → throws")
    void should_throw_when_checkOutDate_is_null() {
        validBookingDTO.setCheckOutDate(null);

        when(userService.getCurrentLoggedInUser()).thenReturn(testUser);
        when(roomRepository.findByIdWithLock(testRoom.getId())).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> bookingService.createBooking(validBookingDTO))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("required");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-BS-18 | findBookingByReferenceNo | referenceNo=null → throws")
    void should_throw_when_referenceNumber_is_null() {
        when(bookingRepository.findByBookingReference(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.findBookingByReferenceNo(null))
                .isInstanceOf(NotFoundException.class);
    }

}
