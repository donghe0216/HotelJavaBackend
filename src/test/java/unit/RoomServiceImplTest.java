package unit;

import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.dtos.RoomDTO;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.RoomRepository;
import com.example.HotelBooking.services.impl.RoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RoomServiceImpl}.
 *
 * <p>Covers: addRoom (field validation, duplicate room number), deleteRoom, getRoomById,
 * getAvailableRooms (date validation, empty result), updateRoom (partial update), and
 * searchRoom (null / blank / no-match inputs).
 *
 * <p>All repository calls are mocked via Mockito; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomServiceImpl Unit Tests")
class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private RoomServiceImpl roomService;

    private RoomDTO validRoomDTO;

    @BeforeEach
    void setUp() {
        validRoomDTO = RoomDTO.builder()
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("100.00"))
                .capacity(2)
                .description("Cozy single room")
                .build();
    }

    @Test
    @DisplayName("TC-RS-01 | addRoom | valid input → saved, returns 200")
    void addRoom_validInput_success() {
        Room savedRoom = new Room();
        RoomDTO savedDTO = RoomDTO.builder().id(1L).build();

        when(modelMapper.map(validRoomDTO, Room.class)).thenReturn(savedRoom);
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);
        when(modelMapper.map(savedRoom, RoomDTO.class)).thenReturn(savedDTO);

        // Passing null for imageFile implicitly covers the if (imageFile != null) branch:
        // saveImageToFrontend is private and cannot be verified directly. If the branch were
        // incorrectly entered, the IO operation would throw in this mock environment and fail the test.
        // Root fix: extract image storage to a mockable ImageStorageService bean.
        Response response = roomService.addRoom(validRoomDTO, null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).containsIgnoringCase("added");
        verify(roomRepository, times(1)).save(any(Room.class));
    }

    @Test
    @DisplayName("TC-RS-02 | addRoom | pricePerNight = 0 → IllegalArgumentException")
    void addRoom_zeroPricePerNight_throwsException() {
        validRoomDTO.setPricePerNight(BigDecimal.ZERO);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price per night");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-03 | addRoom | pricePerNight < 0 → IllegalArgumentException")
    void addRoom_negativePricePerNight_throwsException() {
        validRoomDTO.setPricePerNight(new BigDecimal("-50.00"));

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price per night");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-04 | addRoom | capacity = 0 → IllegalArgumentException")
    void addRoom_zeroCapacity_throwsException() {
        validRoomDTO.setCapacity(0);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-05 | addRoom | capacity < 0 → IllegalArgumentException")
    void addRoom_negativeCapacity_throwsException() {
        validRoomDTO.setCapacity(-1);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-06 | addRoom | roomNumber = 0 → IllegalArgumentException")
    void addRoom_zeroRoomNumber_throwsException() {
        validRoomDTO.setRoomNumber(0);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Room number");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-07 | addRoom | roomNumber < 0 → IllegalArgumentException")
    void addRoom_negativeRoomNumber_throwsException() {
        validRoomDTO.setRoomNumber(-1);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Room number");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-08 | addRoom | type = null → IllegalArgumentException")
    void addRoom_nullType_throwsException() {
        validRoomDTO.setType(null);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Room type");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-09 | addRoom | roomNumber=null → throws (expected behaviour, fix pending)")
    void addRoom_nullRoomNumber_throwsException() {
        validRoomDTO.setRoomNumber(null);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Room number");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-10 | addRoom | pricePerNight=null → throws (expected behaviour, fix pending)")
    void addRoom_nullPricePerNight_throwsException() {
        validRoomDTO.setPricePerNight(null);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price per night");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-11 | addRoom | capacity=null → throws (expected behaviour, fix pending)")
    void addRoom_nullCapacity_throwsException() {
        validRoomDTO.setCapacity(null);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-12 | addRoom | [Bug] duplicate roomNumber → DataIntegrityViolationException, no domain check")
    void should_throw_when_room_number_already_exists() {
        // Design gap: addRoom() calls save() without checking for a duplicate room number first.
        // The DB unique constraint throws DataIntegrityViolationException, which is not caught by
        // the service or GlobalExceptionHandler — the client receives a 500 instead of 409.
        //
        // Fix option A — check-then-act (preferred for this low-concurrency admin operation):
        //   add existsByRoomNumber() to RoomRepository and check before save().
        // Fix option B — insert-then-catch (more robust under high concurrency):
        //   catch DataIntegrityViolationException after save() and rethrow as a domain exception.
        //
        // BookingCodeGenerator uses insert-then-retry because reference collisions are real at scale;
        // addRoom is a low-frequency admin operation, so check-then-act is the cleaner choice here.
        Room roomToSave = new Room();
        when(modelMapper.map(validRoomDTO, Room.class)).thenReturn(roomToSave);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for room_number"));

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(roomRepository, times(1)).save(any(Room.class));
    }

    @Test
    @DisplayName("TC-RS-13 | deleteRoom | unknown id → throws, no deleteById")
    void deleteRoom_nonExistentId_throwsNotFoundException() {
        when(roomRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> roomService.deleteRoom(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");

        verify(roomRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("TC-RS-14 | deleteRoom | valid id → deleted, returns 200")
    void deleteRoom_validId_success() {
        when(roomRepository.existsById(1L)).thenReturn(true);
        when(bookingRepository.existsByRoomIdAndBookingStatusIn(any(), any())).thenReturn(false);

        Response response = roomService.deleteRoom(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).containsIgnoringCase("deleted");
        verify(roomRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("TC-RS-15 | getRoomById | unknown id → throws")
    void getRoomById_nonExistentId_throwsNotFoundException() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("TC-RS-16 | getRoomById | valid id → returns full RoomDTO")
    void getRoomById_success() {
        Room room = Room.builder()
                .id(1L)
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("150.00"))
                .capacity(3)
                .build();

        RoomDTO expectedDTO = RoomDTO.builder()
                .id(1L)
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("150.00"))
                .capacity(3)
                .build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(modelMapper.map(room, RoomDTO.class)).thenReturn(expectedDTO);

        Response response = roomService.getRoomById(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        RoomDTO result = response.getRoom();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRoomNumber()).isEqualTo(101);
        assertThat(result.getPricePerNight()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.getType()).isEqualTo(RoomType.SINGLE);
        verify(roomRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("TC-RS-17 | getAvailableRooms | checkIn in the past → throws")
    void getAvailableRooms_checkInBeforeToday_throwsException() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow  = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> roomService.getAvailableRooms(yesterday, tomorrow, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before today");
    }

    @Test
    @DisplayName("TC-RS-18 | getAvailableRooms | checkOut before checkIn → throws")
    void getAvailableRooms_checkOutBeforeCheckIn_throwsException() {
        LocalDate checkIn  = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(2);

        assertThatThrownBy(() -> roomService.getAvailableRooms(checkIn, checkOut, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before check in");
    }

    @Test
    @DisplayName("TC-RS-19 | getAvailableRooms | checkIn == checkOut → throws")
    void getAvailableRooms_sameDate_throwsException() {
        LocalDate same = LocalDate.now().plusDays(3);

        assertThatThrownBy(() -> roomService.getAvailableRooms(same, same, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("equal");
    }

    @Test
    @DisplayName("TC-RS-20 | getAvailableRooms | valid dates → returns available rooms")
    void getAvailableRooms_success() {
        LocalDate checkIn  = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        Room room = Room.builder().id(1L).type(RoomType.SINGLE).build();
        RoomDTO roomDTO = RoomDTO.builder().id(1L).build();

        when(roomRepository.findAvailableRooms(checkIn, checkOut, RoomType.SINGLE))
                .thenReturn(List.of(room));
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of(roomDTO));

        Response response = roomService.getAvailableRooms(checkIn, checkOut, RoomType.SINGLE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isNotEmpty();
        verify(roomRepository, times(1)).findAvailableRooms(checkIn, checkOut, RoomType.SINGLE);
    }

    @Test
    @DisplayName("TC-RS-21 | getAvailableRooms | no rooms available → empty list, no exception")
    void getAvailableRooms_noRoomsAvailable_returnsEmptyList() {
        LocalDate checkIn  = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        when(roomRepository.findAvailableRooms(checkIn, checkOut, RoomType.SINGLE))
                .thenReturn(List.of());
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of());

        Response response = roomService.getAvailableRooms(checkIn, checkOut, RoomType.SINGLE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isNotNull().isEmpty();
        verify(roomRepository, times(1)).findAvailableRooms(checkIn, checkOut, RoomType.SINGLE);
    }

    @Test
    @DisplayName("TC-RS-22 | updateRoom | price and capacity updated — other fields unchanged")
    void updateRoom_partialUpdate_success() {
        Room existingRoom = Room.builder()
                .id(1L)
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("100.00"))
                .capacity(2)
                .build();

        RoomDTO updateDTO = RoomDTO.builder()
                .id(1L)
                .pricePerNight(new BigDecimal("200.00"))
                .capacity(3)
                .build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Response response = roomService.updateRoom(updateDTO, null);

        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.getPricePerNight()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(saved.getCapacity()).isEqualTo(3);
        assertThat(saved.getRoomNumber()).isEqualTo(101);
        assertThat(saved.getType()).isEqualTo(RoomType.SINGLE);
    }

    @Test
    @DisplayName("TC-RS-23 | updateRoom | all fields updated")
    void updateRoom_allFields_success() {
        Room existingRoom = Room.builder()
                .id(1L)
                .roomNumber(101)
                .type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("100.00"))
                .capacity(2)
                .description("Old description")
                .build();

        RoomDTO updateDTO = RoomDTO.builder()
                .id(1L)
                .roomNumber(202)
                .type(RoomType.DOUBLE)
                .pricePerNight(new BigDecimal("250.00"))
                .capacity(4)
                .description("New description")
                .build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(existingRoom));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        roomService.updateRoom(updateDTO, null);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.getRoomNumber()).isEqualTo(202);
        assertThat(saved.getType()).isEqualTo(RoomType.DOUBLE);
        assertThat(saved.getPricePerNight()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(saved.getCapacity()).isEqualTo(4);
        assertThat(saved.getDescription()).isEqualTo("New description");
    }

    @Test
    @DisplayName("TC-RS-24 | updateRoom | unknown id → throws, no save")
    void updateRoom_nonExistentId_throwsNotFoundException() {
        RoomDTO updateDTO = RoomDTO.builder()
                .id(999L)
                .pricePerNight(new BigDecimal("200.00"))
                .build();

        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.updateRoom(updateDTO, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-25 | searchRoom | keyword match → returns rooms")
    void searchRoom_validKeyword() {
        Room room = Room.builder().id(1L).type(RoomType.SINGLE).description("Cozy single room").build();
        RoomDTO roomDTO = RoomDTO.builder().id(1L).description("Cozy single room").build();

        when(roomRepository.searchRooms("single")).thenReturn(List.of(room));
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of(roomDTO));

        Response response = roomService.searchRoom("single");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isNotEmpty();
    }

    @Test
    @DisplayName("TC-RS-26 | searchRoom | empty string → throws")
    void searchRoom_emptyString_throwsException() {
        assertThatThrownBy(() -> roomService.searchRoom(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-27 | searchRoom | null → IllegalArgumentException")
    void searchRoom_null_throwsException() {
        // isBlank() throws NPE on null; the null check must come first in the guard condition.
        assertThatThrownBy(() -> roomService.searchRoom(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-28 | searchRoom | whitespace-only string → throws")
    void searchRoom_blankString_throwsException() {
        // isEmpty() returns false for "   " — isBlank() is required to catch whitespace-only input.
        // The original source used isEmpty(), allowing blank strings to reach the repository.
        assertThatThrownBy(() -> roomService.searchRoom("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-29 | searchRoom | no match → empty list, no exception")
    void searchRoom_noMatchFound_returnsEmptyList() {
        when(roomRepository.searchRooms("xyzxyzxyz")).thenReturn(List.of());
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of());

        Response response = roomService.searchRoom("xyzxyzxyz");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isNotNull().isEmpty();
    }
}
