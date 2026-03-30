package unit;

import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.dtos.RoomDTO;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NotFoundException;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomServiceImpl Unit Tests")
class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

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

    // ── addRoom ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-01 | addRoom | 合法输入保存成功，返回 200")
    void addRoom_validInput_success() {
        Room savedRoom = new Room();
        RoomDTO savedDTO = RoomDTO.builder().id(1L).build();

        when(modelMapper.map(validRoomDTO, Room.class)).thenReturn(savedRoom);
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);
        when(modelMapper.map(savedRoom, RoomDTO.class)).thenReturn(savedDTO);

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

    // ── deleteRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-09 | deleteRoom | ID 不存在 → NotFoundException，不触发 deleteById")
    void deleteRoom_nonExistentId_throwsNotFoundException() {
        when(roomRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> roomService.deleteRoom(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");

        verify(roomRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("TC-RS-10 | deleteRoom | 有效 ID → 删除成功，返回 200")
    void deleteRoom_validId_success() {
        when(roomRepository.existsById(1L)).thenReturn(true);

        Response response = roomService.deleteRoom(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).containsIgnoringCase("deleted");
        verify(roomRepository, times(1)).deleteById(1L);
    }

    // ── getRoomById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-11 | getRoomById | ID 不存在 → NotFoundException")
    void getRoomById_nonExistentId_throwsNotFoundException() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("TC-RS-12 | getRoomById | 有效 ID → 返回完整 RoomDTO 字段")
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

    // ── getAvailableRooms ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-13 | getAvailableRooms | checkIn 在过去 → InvalidBookingStateAndDateException")
    void getAvailableRooms_checkInBeforeToday_throwsException() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow  = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> roomService.getAvailableRooms(yesterday, tomorrow, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before today");
    }

    @Test
    @DisplayName("TC-RS-14 | getAvailableRooms | checkOut < checkIn → InvalidBookingStateAndDateException")
    void getAvailableRooms_checkOutBeforeCheckIn_throwsException() {
        LocalDate checkIn  = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(2);

        assertThatThrownBy(() -> roomService.getAvailableRooms(checkIn, checkOut, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before check in");
    }

    @Test
    @DisplayName("TC-RS-15 | getAvailableRooms | checkIn == checkOut → InvalidBookingStateAndDateException")
    void getAvailableRooms_sameDate_throwsException() {
        LocalDate same = LocalDate.now().plusDays(3);

        assertThatThrownBy(() -> roomService.getAvailableRooms(same, same, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("equal");
    }

    @Test
    @DisplayName("TC-RS-16 | getAvailableRooms | 合法日期 → 返回可用房间列表")
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

    // ── updateRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-17 | updateRoom | 部分更新 — 只改 price/capacity，其他字段原值保留")
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

        assertThat(saved.getPricePerNight()).isEqualByComparingTo(new BigDecimal("200.00")); // updated
        assertThat(saved.getCapacity()).isEqualTo(3);                                        // updated
        assertThat(saved.getRoomNumber()).isEqualTo(101);                                    // original preserved
        assertThat(saved.getType()).isEqualTo(RoomType.SINGLE);                              // original preserved
    }

    @Test
    @DisplayName("TC-RS-18 | updateRoom | 全字段更新 — 全部生效")
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
    @DisplayName("TC-RS-19 | updateRoom | ID 不存在 → NotFoundException，不触发 save")
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

    // ── searchRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-20 | getAvailableRooms | 无可用房间时返回空列表，不抛异常")
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
    @DisplayName("TC-RS-21 | searchRoom | 有效关键词 → 返回匹配结果")
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
    @DisplayName("TC-RS-22 | searchRoom | 空字符串 → IllegalArgumentException（源码暂缺此校验，此 test 记录期望行为）")
    void searchRoom_emptyString_throwsException() {
        assertThatThrownBy(() -> roomService.searchRoom(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-23 | searchRoom | 关键字无匹配时返回空列表，不抛异常")
    void searchRoom_noMatchFound_returnsEmptyList() {
        when(roomRepository.searchRooms("xyzxyzxyz")).thenReturn(List.of());
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of());

        Response response = roomService.searchRoom("xyzxyzxyz");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isNotNull().isEmpty();
    }
}
