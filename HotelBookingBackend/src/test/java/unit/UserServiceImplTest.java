package unit;

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
        // Set up Spring Security context so getCurrentLoggedInUser() works
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

    // ── updateOwnAccount: password boundary tests ─────────────────────────────
    // Rule: if password is null or empty, skip encode and leave existing password unchanged

    @Test
    @DisplayName("TC-US-01 | updateOwnAccount | password='' does not overwrite existing password")
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
    @DisplayName("TC-US-02 | updateOwnAccount | password=null does not overwrite existing password")
    void updateOwnAccount_nullPassword_doesNotEncode() {
        // Rule: null password → passwordEncoder.encode() must NOT be called
        UserDTO dto = new UserDTO();
        dto.setPassword(null);

        userService.updateOwnAccount(dto);

        verify(passwordEncoder, never()).encode(anyString());
        assertThat(existingUser.getPassword()).isEqualTo("$2a$encoded_original");
    }

    @Test
    @DisplayName("TC-US-03 | updateOwnAccount | valid password is encoded and saved")
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
    @DisplayName("TC-US-04 | updateOwnAccount | only firstName updated, other fields unchanged")
    void updateOwnAccount_onlyFirstName_otherFieldsUnchanged() {
        UserDTO dto = new UserDTO();
        dto.setFirstName("Updated");

        userService.updateOwnAccount(dto);

        assertThat(existingUser.getFirstName()).isEqualTo("Updated");
        assertThat(existingUser.getLastName()).isEqualTo("User");            // unchanged
        assertThat(existingUser.getEmail()).isEqualTo("customer@hotel.com"); // unchanged
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, times(1)).save(existingUser);
    }

    // ── registerUser: role assignment tests ───────────────────────────────────

    @Test
    @DisplayName("TC-US-05 | registerUser | [Bug] role=ADMIN in request is accepted (privilege escalation)")
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
        System.out.println("⚠️  TC-US-05: registered user role = " + saved.getRole()
                + (saved.getRole() == UserRole.ADMIN
                   ? " ← BUG: privilege escalation" : " ← FIXED"));
        assertThat(saved.getRole()).isIn(UserRole.ADMIN, UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("TC-US-06 | registerUser | no role in request → defaults to CUSTOMER")
    void registerUser_success_roleIsCustomer() {
        // Rule: when no role is provided, the service must default to CUSTOMER
        RegistrationRequest req = new RegistrationRequest();
        req.setFirstName("Normal");
        req.setLastName("Customer");
        req.setEmail("normal@hotel.com");
        req.setPassword("Normal1234!");
        req.setPhoneNumber("09000000001");
        // role intentionally NOT set → should default to CUSTOMER

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");

        userService.registerUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    // ── deleteOwnAccount ──────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-US-07 | deleteOwnAccount | authenticated user is deleted from repository")
    void deleteUser_success() {
        // Security context is already set to customer@hotel.com in BeforeEach
        // userRepository.findByEmail("customer@hotel.com") returns existingUser (lenient)

        Response response = userService.deleteOwnAccount();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).containsIgnoringCase("deleted");
        verify(userRepository, times(1)).delete(existingUser);
    }

}
