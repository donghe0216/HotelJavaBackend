package api;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;

/**
 * Base class for all API tests.
 *
 * Convention used throughout:
 *   - adminSpec   → Bearer token with ADMIN role
 *   - customerSpec → Bearer token with CUSTOMER role
 *   - anonSpec    → No auth header (anonymous)
 *
 * The login helper obtains a real JWT from POST /auth/login so these tests
 * run against a live (or @SpringBootTest) server.  Swap BASE_URI / PORT to
 * point at any environment.
 */
public abstract class BaseApiTest {

    // ── Environment ──────────────────────────────────────────────────────────
    protected static final String BASE_URI  = "http://localhost";
    protected static final int    PORT      = 9090;
    protected static final String BASE_PATH = "/api";

    // ── Seed credentials (must exist in the target DB) ───────────────────────
    protected static final String ADMIN_EMAIL    = "admin@hotel.com";
    protected static final String ADMIN_PASSWORD = "Admin1234!";
    protected static final String USER_EMAIL     = "customer@hotel.com";
    protected static final String USER_PASSWORD  = "Customer1234!";

    // ── Shared request specs ──────────────────────────────────────────────────
    protected static RequestSpecification adminSpec;
    protected static RequestSpecification customerSpec;
    protected static RequestSpecification anonSpec;

    // ── Date helpers ─────────────────────────────────────────────────────────
    protected static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    protected static String today()      { return LocalDate.now().format(DATE_FMT); }
    protected static String yesterday()  { return LocalDate.now().minusDays(1).format(DATE_FMT); }
    protected static String tomorrow()   { return LocalDate.now().plusDays(1).format(DATE_FMT); }
    protected static String inDays(int n){ return LocalDate.now().plusDays(n).format(DATE_FMT); }

    // ── One-time setup ────────────────────────────────────────────────────────
    @BeforeAll
    static void globalSetup() {
        RestAssured.baseURI  = BASE_URI;
        RestAssured.port     = PORT;
        RestAssured.basePath = BASE_PATH;

        // Log all requests & responses when running in verbose mode
        if (Boolean.getBoolean("test.verbose")) {
            RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
        }

        String adminToken    = loginAndGetToken(ADMIN_EMAIL,    ADMIN_PASSWORD);
        String customerToken = loginAndGetToken(USER_EMAIL,     USER_PASSWORD);

        adminSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + adminToken)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        customerSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + customerToken)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        anonSpec = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        cleanupStaleTestData();
    }

    // ── Per-test setup (override in subclass if needed) ───────────────────────
    @BeforeEach
    void setUp() { /* hook for subclasses */ }

    // ── Auth helper ───────────────────────────────────────────────────────────
    protected static String loginAndGetToken(String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("email",    email);
        body.put("password", password);

        return given()
                .spec(new RequestSpecBuilder()
                        .setContentType(ContentType.JSON)
                        .setAccept(ContentType.JSON)
                        .build())
                .body(body)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    // ── Generic payload builders ──────────────────────────────────────────────
    protected Map<String, Object> bookingPayload(Long roomId, String checkIn, String checkOut) {
        Map<String, Object> m = new HashMap<>();
        m.put("roomId",       roomId);
        m.put("checkInDate",  checkIn);
        m.put("checkOutDate", checkOut);
        return m;
    }

    protected Map<String, Object> registrationPayload(String email, String password) {
        Map<String, Object> m = new HashMap<>();
        m.put("firstName",   "Test");
        m.put("lastName",    "User");
        m.put("email",       email);
        m.put("password",    password);
        m.put("phoneNumber", "09012345678");
        return m;
    }

    // ── Async helper ──────────────────────────────────────────────────────────
    /**
     * Polls condition every 300ms until it returns true or maxSeconds elapses.
     * Use instead of Thread.sleep() when waiting for async side-effects (e.g. @Async email).
     */
    protected static void pollUntil(int maxSeconds, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(300); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ── Room helper ───────────────────────────────────────────────────────────
    /**
     * Returns the ID of the first room returned by GET /rooms/all.
     * Used instead of hardcoding ID=1 so tests are not brittle against
     * auto-increment gaps or different seed order across environments.
     */
    protected static Long resolveFirstRoomId() {
        Integer id = given()
                .spec(new RequestSpecBuilder()
                        .setContentType(ContentType.JSON)
                        .setAccept(ContentType.JSON)
                        .build())
                .when()
                .get("/rooms/all")
                .then()
                .statusCode(200)
                .extract()
                .path("rooms[0].id");
        return id != null ? id.longValue() : null;
    }

    // ── Test-data cleanup ─────────────────────────────────────────────────────
    /**
     * Cancels all BOOKED/CHECKED_IN bookings and removes test rooms
     * (roomNumber > 200) left over from previous test runs.
     * Called once per test class after specs are initialised, making
     * the suite idempotent across multiple local runs.
     */
    private static void cleanupStaleTestData() {
        // Cancel all active bookings and delete test rooms via DB.
        // Using the API to cancel CHECKED_IN bookings is blocked by state machine validation,
        // so we go directly to the DB for both operations.
        try {
            new ProcessBuilder(
                "docker", "exec", "hotel-mysql",
                "mysql", "-uroot", "-proot", "hotel", "-e",
                "UPDATE bookings SET booking_status='CANCELLED' WHERE booking_status IN ('BOOKED','CHECKED_IN');" +
                "DELETE FROM users WHERE email='not-an-email' OR email LIKE 'temp_email_test_%' OR email LIKE 'new_%@hotel.com' OR email LIKE 'fresh_%@hotel.com' OR email LIKE 'admin_%@hotel.com';" +
                "DELETE b FROM bookings b JOIN rooms r ON b.room_id = r.id WHERE r.room_number > 200;" +
                "DELETE FROM rooms WHERE room_number > 200;"
            ).start().waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) { /* best-effort — Docker may not be available in all envs */ }
    }

    // ── Cleanup helper ────────────────────────────────────────────────────────
    /**
     * Deletes a user account using their own Bearer token.
     * Safe to call even if the account was already deleted (ignores non-200).
     */
    protected static void deleteAccount(String token) {
        if (token == null) return;
        try {
            given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .accept("application/json")
            .when()
                .delete("/users/delete");
        } catch (Exception ignored) { /* best-effort cleanup */ }
    }
}
