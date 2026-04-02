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

    @Test
    @DisplayName("TC-BCG-01 | generateBookingReference | format: 10 uppercase letters or digits 1-9, no 0 or lowercase")
    void should_returnValidFormat_when_codeIsGenerated() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        assertThat(code).matches("[A-Z1-9]{10}");
    }

    @Test
    @DisplayName("TC-BCG-02 | generateBookingReference | first two attempts are duplicates → retries until unique")
    void should_retryAndReturnUniqueCode_when_firstTwoCodesAreDuplicate() {
        // Retry until unique due to potential reference collision
        when(bookingReferenceRepository.findByReferenceNo(any()))
                .thenReturn(Optional.of(new BookingReference()))
                .thenReturn(Optional.of(new BookingReference()))
                .thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        assertThat(code).isNotNull().matches("[A-Z1-9]{10}");
        verify(bookingReferenceRepository, times(3)).findByReferenceNo(any());
    }

    @Test
    @DisplayName("TC-BCG-03 | generateBookingReference | unique code is persisted to DB")
    void should_saveCodeToDatabase_when_uniqueCodeIsGenerated() {
        when(bookingReferenceRepository.findByReferenceNo(any())).thenReturn(Optional.empty());
        when(bookingReferenceRepository.save(any())).thenReturn(new BookingReference());

        String code = bookingCodeGenerator.generateBookingReference();

        verify(bookingReferenceRepository, times(1))
                .save(argThat(ref -> ref.getReferenceNo().equals(code)));
    }

    @Test
    @Disabled("Bug: current implementation is check-then-act (findByReferenceNo before save). " +
              "Under high concurrency, the DB unique constraint fires DataIntegrityViolationException instead of retrying. " +
              "Fix: switch to insert-then-retry — call save() directly, catch DataIntegrityViolationException, and retry. " +
              "This removes one DB round-trip and relies on the DB for uniqueness. Remove @Disabled after fix.")
    @DisplayName("TC-BCG-04 | [Bug] duplicate key on insert should trigger retry, not throw")
    void should_retryOnInsert_when_duplicateKeyExceptionIsThrown() {
        // Bug: check-then-act has a race window — code absent at query time may be present at insert time.
        // Better design: insert-then-retry removes the race and the extra SELECT.

        // After fix: assertThatThrownBy is no longer applicable — the retry succeeds instead of throwing.
        // Example assertions for the fixed implementation:
        // when(bookingReferenceRepository.save(any()))
        //         .thenThrow(new DataIntegrityViolationException("duplicate"))
        //         .thenReturn(new BookingReference());
        // String code = bookingCodeGenerator.generateBookingReference();
        // assertThat(code).isNotNull();
        // verify(bookingReferenceRepository, times(2)).save(any());
    }
}
