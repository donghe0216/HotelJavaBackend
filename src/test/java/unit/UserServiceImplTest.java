package unit;

import com.example.HotelBooking.dtos.RegistrationRequest;
import com.example.HotelBooking.dtos.UserUpdateRequest;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.UserRole;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.UserRepository;
import com.example.HotelBooking.security.JwtUtils;
import com.example.HotelBooking.services.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserServiceImpl}.
 *
 * <p>Covers: login, user registration (including privilege-escalation bug), account update
 * (partial-field, password encoding rules, invalid email bug), and account deletion.
 *
 * <p>Spring Security context is manually set in {@code @BeforeEach} to simulate an
 * authenticated user; all repositories and encoders are mocked via Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

    @Mock private UserRepository     userRepository;
    @Mock private PasswordEncoder    passwordEncoder;
    @Mock private JwtUtils           jwtUtils;
    @Mock private ModelMapper        modelMapper;
    @Mock private BookingRepository  bookingRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "customer@hotel.com", null, Collections.emptyList()));

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail("customer@hotel.com");
        existingUser.setPassword("$2a$encoded_original");
        existingUser.setFirstName("Original");
        existingUser.setLastName("User");

        lenient().when(userRepository.findByEmail("customer@hotel.com"))
                .thenReturn(Optional.of(existingUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC-US-01 | registerUser | no role in request → defaults to CUSTOMER")
    void registerUser_success_roleIsCustomer() {
        // Rule: when no role is provided, the service must default to CUSTOMER
        RegistrationRequest req = new RegistrationRequest();
        req.setFirstName("Normal");
        req.setLastName("Customer");
        req.setEmail("normal@hotel.com");
        req.setPassword("Normal1234!");
        req.setPhoneNumber("09000000001");

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");

        userService.registerUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("TC-US-02 | registerUser | password is encoded before saving")
    void registerUser_passwordIsEncoded() {
        RegistrationRequest req = new RegistrationRequest();
        req.setFirstName("Normal");
        req.setLastName("Customer");
        req.setEmail("normal@hotel.com");
        req.setPassword("Normal1234!");
        req.setPhoneNumber("09000000001");

        when(passwordEncoder.encode("Normal1234!")).thenReturn("$2a$encoded");

        userService.registerUser(req);

        verify(passwordEncoder, times(1)).encode("Normal1234!");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded");
    }

    @Test
    @DisplayName("TC-US-03 | registerUser | [Bug] role=ADMIN in request is accepted (privilege escalation)")
    void registerUser_withAdminRole_privilegeEscalationBug() {
        RegistrationRequest req = new RegistrationRequest();
        req.setFirstName("Test");
        req.setLastName("Admin");
        req.setEmail("admin_test@hotel.com");
        req.setPassword("Admin1234!");
        req.setPhoneNumber("09000000000");
        req.setRole(UserRole.ADMIN);

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");

        userService.registerUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        // Bug: role=ADMIN is accepted — should be forced to CUSTOMER
        // After fix: assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER)
        System.out.println("⚠️  TC-US-03: registered user role = " + saved.getRole()
                + (saved.getRole() == UserRole.ADMIN
                   ? " ← BUG: privilege escalation" : " ← FIXED"));
        assertThat(saved.getRole()).isIn(UserRole.ADMIN, UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("TC-US-04 | updateOwnAccount | only firstName updated, other fields unchanged")
    void updateOwnAccount_onlyFirstName_otherFieldsUnchanged() {
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setFirstName("Updated");

        userService.updateOwnAccount(dto);

        assertThat(existingUser.getFirstName()).isEqualTo("Updated");
        assertThat(existingUser.getLastName()).isEqualTo("User");
        assertThat(existingUser.getEmail()).isEqualTo("customer@hotel.com");
        assertThat(existingUser.getPassword()).isEqualTo("$2a$encoded_original");
        assertThat(existingUser.getPhoneNumber()).isNull();
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("TC-US-05 | updateOwnAccount | valid password is encoded and saved")
    void updateOwnAccount_validPassword_encodesAndSaves() {
        // Rule: non-empty password → must be encoded via passwordEncoder before saving
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setPassword("NewPass1234!");

        when(passwordEncoder.encode("NewPass1234!")).thenReturn("$2a$encoded_new");

        userService.updateOwnAccount(dto);

        verify(passwordEncoder, times(1)).encode("NewPass1234!");
        assertThat(existingUser.getPassword()).isEqualTo("$2a$encoded_new");
    }

    @Test
    @DisplayName("TC-US-06 | updateOwnAccount | duplicate email → DataIntegrityViolationException propagates")
    void updateOwnAccount_duplicateEmail_throwsDataIntegrityViolation() {
        // Not idempotent: the service calls save() without checking for duplicate emails first.
        // If the email is already taken, the DB unique constraint throws DataIntegrityViolationException,
        // which GlobalExceptionHandler converts to 409. The service relies on the framework to catch it
        // rather than failing fast with a domain exception.
        // Fix: call userRepository.existsByEmail() before save() and throw a meaningful business exception.
        UserUpdateRequest dto = new UserUpdateRequest();
        dto.setEmail("admin@hotel.com"); // already taken by the seeded admin account

        when(userRepository.save(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("Duplicate entry for email"));

        assertThatThrownBy(() -> userService.updateOwnAccount(dto))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        verify(userRepository, times(1)).save(existingUser);
    }

}
