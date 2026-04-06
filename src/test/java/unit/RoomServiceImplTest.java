package unit;

import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.dtos.RoomDTO;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NameValueRequiredException;
import com.example.HotelBooking.exceptions.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomServiceImpl Unit Tests")
class RoomServiceImplTest {

    @Mock private RoomRepository roomRepository;
    @Mock private ModelMapper    modelMapper;

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

    // Shared stub for the success path of addRoom
    private void stubAddRoomSuccess() {
        Room room = new Room();
        when(modelMapper.map(any(RoomDTO.class), eq(Room.class))).thenReturn(room);
        when(roomRepository.save(any())).thenReturn(room);
        when(modelMapper.map(any(Room.class), eq(RoomDTO.class))).thenReturn(new RoomDTO());
    }

    // ── addRoom ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-01 | addRoom | valid input → saved, returns 200")
    void addRoom_validInput_success() {
        stubAddRoomSuccess();

        Response response = roomService.addRoom(validRoomDTO, null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).containsIgnoringCase("added");
        verify(roomRepository).save(any());
    }

    @Test
    @DisplayName("TC-RS-02 | addRoom | pricePerNight = 0 → throws")
    void addRoom_zeroPricePerNight_throws() {
        validRoomDTO.setPricePerNight(BigDecimal.ZERO);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("pricePerNight");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-03 | addRoom | pricePerNight = -1 → throws")
    void addRoom_negativePricePerNight_throws() {
        validRoomDTO.setPricePerNight(new BigDecimal("-1.00"));
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("pricePerNight");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-04 | addRoom | pricePerNight = 0.01（最小合法値）→ saved")
    void addRoom_minValidPricePerNight_success() {
        validRoomDTO.setPricePerNight(new BigDecimal("0.01"));
        stubAddRoomSuccess();
        assertThat(roomService.addRoom(validRoomDTO, null).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-RS-05 | addRoom | capacity = 0 → throws")
    void addRoom_zeroCapacity_throws() {
        validRoomDTO.setCapacity(0);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("capacity");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-06 | addRoom | capacity = -1 → throws")
    void addRoom_negativeCapacity_throws() {
        validRoomDTO.setCapacity(-1);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("capacity");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-07 | addRoom | capacity = 1（最小合法値）→ saved")
    void addRoom_minValidCapacity_success() {
        validRoomDTO.setCapacity(1);
        stubAddRoomSuccess();
        assertThat(roomService.addRoom(validRoomDTO, null).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-RS-08 | addRoom | roomNumber = 0 → throws")
    void addRoom_zeroRoomNumber_throws() {
        validRoomDTO.setRoomNumber(0);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("roomNumber");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-09 | addRoom | roomNumber = -1 → throws")
    void addRoom_negativeRoomNumber_throws() {
        validRoomDTO.setRoomNumber(-1);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("roomNumber");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-10 | addRoom | roomNumber = 1（最小合法値）→ saved")
    void addRoom_minValidRoomNumber_success() {
        validRoomDTO.setRoomNumber(1);
        stubAddRoomSuccess();
        assertThat(roomService.addRoom(validRoomDTO, null).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-RS-11 | addRoom | type = null → throws")
    void addRoom_nullType_throws() {
        validRoomDTO.setType(null);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("type");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-12 | addRoom | pricePerNight = null → throws")
    void addRoom_nullPricePerNight_throws() {
        validRoomDTO.setPricePerNight(null);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("pricePerNight");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-13 | addRoom | capacity = null → throws")
    void addRoom_nullCapacity_throws() {
        validRoomDTO.setCapacity(null);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("capacity");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-14 | addRoom | roomNumber = null → throws")
    void addRoom_nullRoomNumber_throws() {
        validRoomDTO.setRoomNumber(null);
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("roomNumber");
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-15 | addRoom | duplicate roomNumber → DataIntegrityViolationException")
    void addRoom_duplicateRoomNumber_throws() {
        // BUG: No service-layer duplicate check.
        // Current behavior relies on DB unique constraint, causing DataIntegrityViolationException
        // instead of a domain-specific 409 response.
        //
        // Fix: Add existsByRoomNumber() check before save() to return a user-friendly error. when(modelMapper.map(any(RoomDTO.class), eq(Room.class))).thenReturn(new Room());
        when(roomRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        // Asserts current broken behavior — tighten to NameValueRequiredException after fix
        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(roomRepository).save(any());
    }

    // ── deleteRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-16 | deleteRoom | unknown id → throws, no deleteById")
    void deleteRoom_notFound_throws() {
        when(roomRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> roomService.deleteRoom(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
        verify(roomRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("TC-RS-17 | deleteRoom | valid id → deleted, returns 200")
    void deleteRoom_success() {
        long id = 1L;
        when(roomRepository.existsById(id)).thenReturn(true);
        Response response = roomService.deleteRoom(id);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(roomRepository).deleteById(id);
    }

    // ── getRoomById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-18 | getRoomById | unknown id → throws")
    void getRoomById_notFound_throws() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> roomService.getRoomById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("TC-RS-19 | getRoomById | valid id → returns room")
    void getRoomById_success() {
        Room room = Room.builder().id(1L).roomNumber(101).build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(modelMapper.map(room, RoomDTO.class)).thenReturn(RoomDTO.builder().id(1L).build());

        Response response = roomService.getRoomById(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRoom().getId()).isEqualTo(1L);
    }

    // ── getAvailableRooms ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-20 | getAvailableRooms | checkIn in the past → throws")
    void getAvailableRooms_checkInBeforeToday_throws() {
        assertThatThrownBy(() -> roomService.getAvailableRooms(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before today");
    }

    @Test
    @DisplayName("TC-RS-21 | getAvailableRooms | checkOut before checkIn → throws")
    void getAvailableRooms_checkOutBeforeCheckIn_throws() {
        assertThatThrownBy(() -> roomService.getAvailableRooms(
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(2), RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before check in");
    }

    @Test
    @DisplayName("TC-RS-22 | getAvailableRooms | checkIn == checkOut → throws")
    void getAvailableRooms_sameDate_throws() {
        LocalDate same = LocalDate.now().plusDays(3);
        assertThatThrownBy(() -> roomService.getAvailableRooms(same, same, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("equal");
    }

    @Test
    @DisplayName("TC-RS-23 | getAvailableRooms | valid dates → returns rooms")
    void getAvailableRooms_success() {
        LocalDate checkIn  = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);
        when(roomRepository.findAvailableRooms(checkIn, checkOut, RoomType.SINGLE))
                .thenReturn(List.of(new Room()));
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of(new RoomDTO()));

        Response response = roomService.getAvailableRooms(checkIn, checkOut, RoomType.SINGLE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isNotEmpty();
    }

    @Test
    @DisplayName("TC-RS-24 | getAvailableRooms | no rooms available → empty list, no exception")
    void getAvailableRooms_noRooms_returnsEmptyList() {
        LocalDate checkIn  = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);
        when(roomRepository.findAvailableRooms(checkIn, checkOut, RoomType.SINGLE))
                .thenReturn(List.of());
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of());

        Response response = roomService.getAvailableRooms(checkIn, checkOut, RoomType.SINGLE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isEmpty();
    }

    // ── updateRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-25 | updateRoom | partial update — only specified fields change")
    void updateRoom_partialUpdate_success() {
        Room existing = Room.builder()
                .id(1L).roomNumber(101).type(RoomType.SINGLE)
                .pricePerNight(new BigDecimal("100.00")).capacity(2)
                .build();
        RoomDTO update = RoomDTO.builder()
                .id(1L).pricePerNight(new BigDecimal("200.00")).capacity(3)
                .build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        roomService.updateRoom(update, null);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();

        assertThat(saved.getPricePerNight()).isEqualByComparingTo("200.00");
        assertThat(saved.getCapacity()).isEqualTo(3);
        // Fields not in the update DTO must be unchanged
        assertThat(saved.getRoomNumber()).isEqualTo(101);
        assertThat(saved.getType()).isEqualTo(RoomType.SINGLE);
    }

    @Test
    @DisplayName("TC-RS-26 | updateRoom | roomNumber already taken → DataIntegrityViolationException")
    void updateRoom_duplicateRoomNumber_throws() {
        // [Bug] No service-layer duplicate check for roomNumber.
        // Same issue as addRoom (TC-RS-15): relies on DB unique constraint.
        // Fix: add existsByRoomNumber() check before save().
        Room existing = Room.builder().id(1L).roomNumber(101).build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        // Asserts current broken behavior — tighten to NameValueRequiredException after fix
        assertThatThrownBy(() -> roomService.updateRoom(RoomDTO.builder().id(1L).roomNumber(202).build(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(roomRepository).save(any());
    }

    // ── searchRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-27 | searchRoom | empty string → throws")
    void searchRoom_emptyString_throws() {
        assertThatThrownBy(() -> roomService.searchRoom(""))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-28 | searchRoom | null → throws")
    void searchRoom_null_throws() {
        // isBlank() throws NPE on null — null check must come first in the guard
        assertThatThrownBy(() -> roomService.searchRoom(null))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-29 | searchRoom | whitespace-only → throws")
    void searchRoom_blank_throws() {
        // isEmpty() returns false for "   " — isBlank() is required here
        assertThatThrownBy(() -> roomService.searchRoom("   "))
                .isInstanceOf(NameValueRequiredException.class)
                .hasMessageContaining("keyword cannot be empty");
    }
}
