package unit;

import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.dtos.RoomDTO;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
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

        // imageFile = null 隐式验证：saveImageToFrontend 是 RoomServiceImpl 的 private 方法，
        // 无法通过 Mockito verify() 直接断言"未被调用"。
        // 此处通过 null 入参 + 测试正常通过来隐式覆盖 if (imageFile != null) 分支：
        // 若该分支被错误执行，saveImageToFrontend 内部的 IO 操作会在 mock 环境抛异常，
        // 导致 test 失败。局限性：不如显式 verify 直观；根本解法是将 saveImageToFrontend
        // 提取到独立的 ImageStorageService bean，改为可 mock 的依赖。
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
    @DisplayName("TC-RS-09 | addRoom | roomNumber 为 null → IllegalArgumentException（源码暂缺此校验，此 test 记录期望行为）")
    void addRoom_nullRoomNumber_throwsException() {
        validRoomDTO.setRoomNumber(null);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Room number");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-10 | addRoom | pricePerNight 为 null → IllegalArgumentException（源码暂缺此校验，此 test 记录期望行为）")
    void addRoom_nullPricePerNight_throwsException() {
        validRoomDTO.setPricePerNight(null);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price per night");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-11 | addRoom | capacity 为 null → IllegalArgumentException（源码暂缺此校验，此 test 记录期望行为）")
    void addRoom_nullCapacity_throwsException() {
        validRoomDTO.setCapacity(null);

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-12 | addRoom | roomNumber 已存在 → DataIntegrityViolationException（源码无显式重复校验）")
    void should_throw_when_room_number_already_exists() {
        // [面试素材] Design gap: addRoom() 无显式 roomNumber 重复校验，直接调用 save()。
        // Room 实体上有 @Column(unique = true)，所以 DB 会抛 DataIntegrityViolationException，
        // 但该异常未被 Service 或 GlobalExceptionHandler 捕获 → 前端收到 500，而非友好的 409。
        //
        // 修复方案对比（面试可以展开讲）：
        //
        // 方案A — check-then-act（推荐用于本场景）：
        //   RoomRepository 加 existsByRoomNumber(Integer)，
        //   addRoom() 调用 save() 前先查，冲突则抛业务异常。
        //   优点：错误信息明确，易测试。
        //   缺点：TOCTOU 窗口（并发时仍可能绕过检查）。
        //   可接受原因：addRoom 是管理员低频操作，并发概率极低。
        //
        // 方案B — insert-then-catch（高并发场景更健壮）：
        //   直接 save()，catch DataIntegrityViolationException，
        //   再抛业务异常。真正依赖 DB unique constraint 做保证，无 TOCTOU。
        //   缺点：DataIntegrityViolationException 可能来自多个字段，需解析 cause。
        //
        // 对比点（面试素材）：
        //   BookingCodeGenerator 选 insert-then-retry，因为 bookingReference 生成频率高、
        //   并发窗口真实存在；而 addRoom 选 check-then-act 更合适，
        //   展示了"方案选型要结合并发量"的判断力。
        Room roomToSave = new Room();
        when(modelMapper.map(validRoomDTO, Room.class)).thenReturn(roomToSave);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for room_number"));

        assertThatThrownBy(() -> roomService.addRoom(validRoomDTO, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(roomRepository, times(1)).save(any(Room.class));
    }

    // ── deleteRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-13 | deleteRoom | ID 不存在 → NotFoundException，不触发 deleteById")
    void deleteRoom_nonExistentId_throwsNotFoundException() {
        when(roomRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> roomService.deleteRoom(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");

        verify(roomRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("TC-RS-14 | deleteRoom | 有效 ID → 删除成功，返回 200")
    void deleteRoom_validId_success() {
        when(roomRepository.existsById(1L)).thenReturn(true);

        Response response = roomService.deleteRoom(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).containsIgnoringCase("deleted");
        verify(roomRepository, times(1)).deleteById(1L);
    }

    // ── getRoomById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-15 | getRoomById | ID 不存在 → NotFoundException")
    void getRoomById_nonExistentId_throwsNotFoundException() {
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("TC-RS-16 | getRoomById | 有效 ID → 返回完整 RoomDTO 字段")
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
    @DisplayName("TC-RS-17 | getAvailableRooms | checkIn 在过去 → InvalidBookingStateAndDateException")
    void getAvailableRooms_checkInBeforeToday_throwsException() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow  = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> roomService.getAvailableRooms(yesterday, tomorrow, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before today");
    }

    @Test
    @DisplayName("TC-RS-18 | getAvailableRooms | checkOut < checkIn → InvalidBookingStateAndDateException")
    void getAvailableRooms_checkOutBeforeCheckIn_throwsException() {
        LocalDate checkIn  = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(2);

        assertThatThrownBy(() -> roomService.getAvailableRooms(checkIn, checkOut, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("before check in");
    }

    @Test
    @DisplayName("TC-RS-19 | getAvailableRooms | checkIn == checkOut → InvalidBookingStateAndDateException")
    void getAvailableRooms_sameDate_throwsException() {
        LocalDate same = LocalDate.now().plusDays(3);

        assertThatThrownBy(() -> roomService.getAvailableRooms(same, same, RoomType.SINGLE))
                .isInstanceOf(InvalidBookingStateAndDateException.class)
                .hasMessageContaining("equal");
    }

    @Test
    @DisplayName("TC-RS-20 | getAvailableRooms | 合法日期 → 返回可用房间列表")
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
    @DisplayName("TC-RS-21 | getAvailableRooms | 无可用房间时返回空列表，不抛异常")
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

    // ── updateRoom ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RS-22 | updateRoom | 部分更新 — 只改 price/capacity，其他字段原值保留")
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
    @DisplayName("TC-RS-23 | updateRoom | 全字段更新 — 全部生效")
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
    @DisplayName("TC-RS-24 | updateRoom | ID 不存在 → NotFoundException，不触发 save")
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
    @DisplayName("TC-RS-25 | searchRoom | 有效关键词 → 返回匹配结果")
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
    @DisplayName("TC-RS-26 | searchRoom | 空字符串 \"\" → IllegalArgumentException")
    void searchRoom_emptyString_throwsException() {
        assertThatThrownBy(() -> roomService.searchRoom(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-27 | searchRoom | null → IllegalArgumentException")
    void searchRoom_null_throwsException() {
        // null 走 input == null 分支；isBlank() 单独处理不到 null，两者须同时校验
        assertThatThrownBy(() -> roomService.searchRoom(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-28 | searchRoom | 纯空白字符串 \"   \" → IllegalArgumentException")
    void searchRoom_blankString_throwsException() {
        // [面试素材] isEmpty() 对 "   " 返回 false，必须用 isBlank()。
        // 本项目原始源码用的是 isEmpty()，导致空白字符串透传到 repository，
        // 对齐 GitHub 版本后改为 isBlank()，此 test 验证修复生效。
        assertThatThrownBy(() -> roomService.searchRoom("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword cannot be empty");
    }

    @Test
    @DisplayName("TC-RS-29 | searchRoom | 关键字无匹配时返回空列表，不抛异常")
    void searchRoom_noMatchFound_returnsEmptyList() {
        when(roomRepository.searchRooms("xyzxyzxyz")).thenReturn(List.of());
        when(modelMapper.map(any(), (Type) any())).thenReturn(List.of());

        Response response = roomService.searchRoom("xyzxyzxyz");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRooms()).isNotNull().isEmpty();
    }
}
