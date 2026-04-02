package unit;

import com.example.HotelBooking.entities.BookingReference;
import com.example.HotelBooking.repositories.BookingReferenceRepository;
import com.example.HotelBooking.services.BookingCodeGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCodeGeneratorTest {

    @Mock
    private BookingReferenceRepository bookingReferenceRepository;

    @InjectMocks
    private BookingCodeGenerator bookingCodeGenerator;

    // ─────────────────────────────────────────────────────────────
    // Format checks
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("生成的预订码应为10位大写字母或1-9数字，不含0和小写字母")
    void should_returnValidFormat_when_codeIsGenerated() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        // [A-Z1-9]{10} 同时验证长度和字符集，TC 合一
        assertThat(code).matches("[A-Z1-9]{10}");
    }

    // ─────────────────────────────────────────────────────────────
    // Uniqueness / retry behavior
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("前两次生成的码已存在时，应重试直到生成唯一码")
    void should_retryAndReturnUniqueCode_when_firstTwoCodesAreDuplicate() {
        // First two calls return duplicate, third returns empty (unique)
        when(bookingReferenceRepository.findByReferenceNo(any()))
                .thenReturn(Optional.of(new BookingReference()))
                .thenReturn(Optional.of(new BookingReference()))
                .thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        assertThat(code).isNotNull().matches("[A-Z1-9]{10}");
        verify(bookingReferenceRepository, times(3)).findByReferenceNo(any());
    }

    // ─────────────────────────────────────────────────────────────
    // Persistence
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("生成成功后应将预订码保存到数据库")
    void should_saveCodeToDatabase_when_uniqueCodeIsGenerated() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        verify(bookingReferenceRepository, times(1))
                .save(argThat(ref -> ref.getReferenceNo().equals(code)));
    }

    // ─────────────────────────────────────────────────────────────
    // Bug documentation
    // ─────────────────────────────────────────────────────────────

    @Test
    @Disabled("⚠️ BUG: 当前实现是 check-then-act（先查再插），极端并发下 DB unique constraint 触发时" +
              "会直接抛 DataIntegrityViolationException 而非 retry。" +
              "推荐修复：改为 insert-then-retry — 直接 save()，捕获 DuplicateKeyException 后重试，" +
              "省去一次 findByReferenceNo 查询，并真正依赖 DB 做唯一性保证。" +
              "修复后删除此 @Disabled 并放开下方断言。")
    @DisplayName("【BUG】insert 遇到 duplicate key 时应自动 retry，而非直接抛异常")
    void should_retryOnInsert_when_duplicateKeyExceptionIsThrown() {
        // [面试素材] check-then-act 的问题：
        //   查询时 code 不存在 ≠ 插入时 code 不存在（并发窗口）。
        //   更好的设计：直接 insert，依赖 DB unique constraint，
        //   捕获 DuplicateKeyException 后 retry —— 减少一次 DB 查询，且并发安全。
        //
        // 修复步骤：
        //   1. 去掉 isBookingReferenceExist() 的 do-while 检查
        //   2. 直接 save()，catch DataIntegrityViolationException → 重试
        //   3. 删除 @Disabled，放开下面的断言

        // assertThatThrownBy 不适用于新设计（修复后应 retry 成功，而非抛异常）
        // 修复后的断言示例：
        // when(bookingReferenceRepository.save(any()))
        //         .thenThrow(new DataIntegrityViolationException("duplicate"))
        //         .thenReturn(new BookingReference());
        // String code = bookingCodeGenerator.generateBookingReference();
        // assertThat(code).isNotNull();
        // verify(bookingReferenceRepository, times(2)).save(any());
    }
}
