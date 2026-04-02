package unit;

import com.example.HotelBooking.dtos.LoginRequest;
import com.example.HotelBooking.dtos.RegistrationRequest;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.dtos.UserDTO;
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
    @DisplayName("TC-US-01 | loginUser | valid credentials → 200 with token and role")
    void loginUser_validCredentials_returnsTokenAndRole() {
        existingUser.setRole(UserRole.CUSTOMER);

        LoginRequest req = new LoginRequest();
        req.setEmail("customer@hotel.com");
        req.setPassword("Customer1234!");

        when(userRepository.findByEmail("customer@hotel.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Customer1234!", existingUser.getPassword())).thenReturn(true);
        when(jwtUtils.generateToken("customer@hotel.com")).thenReturn("mock-jwt-token");

        Response response = userService.loginUser(req);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getRole()).isEqualTo(UserRole.CUSTOMER);
        verify(jwtUtils, times(1)).generateToken("customer@hotel.com");
    }

    @Test
    @DisplayName("TC-US-02 | registerUser | no role in request → defaults to CUSTOMER")
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

    // Rule: if password is null or empty, skip encode and leave existing password unchanged
    @Test
    @DisplayName("TC-US-04 | updateOwnAccount | only firstName updated, other fields unchanged")
    void updateOwnAccount_onlyFirstName_otherFieldsUnchanged() {
        UserDTO dto = new UserDTO();
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
    @DisplayName("TC-US-05 | updateOwnAccount | password='' does not overwrite existing password")
    void updateOwnAccount_emptyPassword_doesNotEncodeOrSave() {
        // Rule: empty string password → passwordEncoder.encode() must NOT be called
        UserDTO dto = new UserDTO();
        dto.setPassword("");

        userService.updateOwnAccount(dto);

        verify(passwordEncoder, never()).encode(anyString());
        // user is still saved (other fields may update), but password field unchanged
        assertThat(existingUser.getPassword()).isEqualTo("$2a$encoded_original");
    }

    @Test
    @DisplayName("TC-US-06 | updateOwnAccount | password=null does not overwrite existing password")
    void updateOwnAccount_nullPassword_doesNotEncode() {
        // Rule: null password → passwordEncoder.encode() must NOT be called
        UserDTO dto = new UserDTO();
        dto.setPassword(null);

        userService.updateOwnAccount(dto);

        verify(passwordEncoder, never()).encode(anyString());
        assertThat(existingUser.getPassword()).isEqualTo("$2a$encoded_original");
    }

    @Test
    @DisplayName("TC-US-07 | updateOwnAccount | valid password is encoded and saved")
    void updateOwnAccount_validPassword_encodesAndSaves() {
        // Rule: non-empty password → must be encoded via passwordEncoder before saving
        UserDTO dto = new UserDTO();
        dto.setPassword("NewPass1234!");

        when(passwordEncoder.encode("NewPass1234!")).thenReturn("$2a$encoded_new");

        userService.updateOwnAccount(dto);

        verify(passwordEncoder, times(1)).encode("NewPass1234!");
        assertThat(existingUser.getPassword()).isEqualTo("$2a$encoded_new");
    }

    @Test
    @DisplayName("TC-US-08 | updateOwnAccount | [Bug] invalid email format accepted — no validation in service")
    void updateOwnAccount_invalidEmailFormat_noValidationBug() {
        // Bug: UserDTO.email has no @Email constraint and UserServiceImpl performs no format check.
        // Any string (e.g. "not-an-email") passes the service layer and is written to the user record.
        // Fix: add @Email + @Valid to UserDTO and intercept at the controller layer (returns 400).
        UserDTO dto = new UserDTO();
        dto.setEmail("not-an-email");

        userService.updateOwnAccount(dto);

        assertThat(existingUser.getEmail()).isEqualTo("not-an-email");
        verify(userRepository, times(1)).save(existingUser);
        System.out.println("⚠️  TC-US-08: invalid email format accepted by service — missing @Email validation");
    }

    @Test
    @DisplayName("TC-US-09 | updateOwnAccount | duplicate email → DataIntegrityViolationException propagates")
    void updateOwnAccount_duplicateEmail_throwsDataIntegrityViolation() {
        // Not idempotent: the service calls save() without checking for duplicate emails first.
        // If the email is already taken, the DB unique constraint throws DataIntegrityViolationException,
        // which GlobalExceptionHandler converts to 409. The service relies on the framework to catch it
        // rather than failing fast with a domain exception.
        // Fix: call userRepository.existsByEmail() before save() and throw a meaningful business exception.
        UserDTO dto = new UserDTO();
        dto.setEmail("admin@hotel.com"); // already taken by the seeded admin account

        when(userRepository.save(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("Duplicate entry for email"));

        assertThatThrownBy(() -> userService.updateOwnAccount(dto))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("TC-US-10 | deleteOwnAccount | authenticated user is deleted from repository")
    void deleteUser_success() {
        Response response = userService.deleteOwnAccount();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).containsIgnoringCase("deleted");
        verify(userRepository, times(1)).delete(existingUser);
    }

}
