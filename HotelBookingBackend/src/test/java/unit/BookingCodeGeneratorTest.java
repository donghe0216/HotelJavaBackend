package unit;

import com.example.HotelBooking.entities.BookingReference;
import com.example.HotelBooking.repositories.BookingReferenceRepository;
import com.example.HotelBooking.services.BookingCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

// Unit tests for BookingCodeGenerator.
// Verifies format, uniqueness, and persistence behavior.
// 予約コードの形式・ユニーク性・保存処理をテスト
@ExtendWith(MockitoExtension.class)
class BookingCodeGeneratorTest {

    @Mock
    private BookingReferenceRepository bookingReferenceRepository;

    @InjectMocks
    private BookingCodeGenerator bookingCodeGenerator;

    // ─────────────────────────────────────────────────────────────
    // Format checks
    // コードの形式チェック
    // ─────────────────────────────────────────────────────────────

    @Test
    void generatedCode_shouldBe10CharactersLong() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        assertThat(code).hasSize(10);
    }

    @Test
    void generatedCode_shouldContainOnlyUppercaseAlphanumeric() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        // Only A-Z and 1-9 are allowed (no 0, no lowercase)
        assertThat(code).matches("[A-Z1-9]{10}");
    }

    // ─────────────────────────────────────────────────────────────
    // Uniqueness behavior
    // 重複チェックのロジックをテスト
    // ─────────────────────────────────────────────────────────────

    @Test
    void whenFirstCodeIsDuplicate_shouldRetryAndReturnUniqueCode() {
        // First call returns a duplicate, second call returns empty (unique)
        when(bookingReferenceRepository.findByReferenceNo(any()))
                .thenReturn(Optional.of(new BookingReference()))
                .thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        assertThat(code).isNotNull().hasSize(10);
        // findByReferenceNo should be called at least twice due to the retry
        verify(bookingReferenceRepository, atLeast(2)).findByReferenceNo(any());
    }

    // ─────────────────────────────────────────────────────────────
    // Persistence check
    // 生成したコードがDBに保存されるか確認
    // ─────────────────────────────────────────────────────────────

    @Test
    void generatedCode_shouldBeSavedToDatabase() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        verify(bookingReferenceRepository, times(1))
                .save(argThat(ref -> ref.getReferenceNo().equals(code)));
    }

    // ─────────────────────────────────────────────────────────────
    // Multiple calls produce different codes
    // 複数回呼び出しても異なるコードが返るか確認
    // ─────────────────────────────────────────────────────────────

    @Test
    void multipleCalls_shouldReturnDifferentCodes() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            codes.add(bookingCodeGenerator.generateBookingReference());
        }

        // Very unlikely to get duplicates with a 10-char alphanumeric code
        assertThat(codes).hasSizeGreaterThan(15);
    }
}
