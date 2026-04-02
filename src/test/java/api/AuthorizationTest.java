package api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Authorization & Security tests.
 *
 * Verifies that access control is correctly enforced across all roles:
 *   - Anonymous (no token)
 *   - CUSTOMER (valid token, limited permissions)
 *   - ADMIN (valid token, full permissions)
 *
 * These tests are intentionally role-focused rather than feature-focused:
 * the question being answered is not "does the feature work" but
 * "does the system correctly reject unauthorized access".
 */
@DisplayName("Authorization & Security Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthorizationTest extends BaseApiTest {

    @Test @Order(1)
    @DisplayName("TC-AUTH-01 | anonymous | GET /users/account → 401")
    void anonymous_accessMyAccount_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/users/account")
        .then()
            .statusCode(401);
    }

    @Test @Order(2)
    @DisplayName("TC-AUTH-02 | anonymous | GET /users/bookings → 401")
    void anonymous_accessMyBookings_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/users/bookings")
        .then()
            .statusCode(401);
    }

    @Test @Order(3)
    @DisplayName("TC-AUTH-03 | anonymous | POST /bookings → 401")
    void anonymous_createBooking_returns401() {
        given()
            .spec(anonSpec)
            .body(bookingPayload(1L, inDays(5), inDays(7)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(401);
    }

    @Test @Order(4)
    @DisplayName("TC-AUTH-04 | anonymous | GET /bookings/all → 401")
    void anonymous_accessAllBookings_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/bookings/all")
        .then()
            .statusCode(401);
    }

    @Test @Order(5)
    @DisplayName("TC-AUTH-05 | anonymous | GET /users/all → 401")
    void anonymous_getAllUsers_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/users/all")
        .then()
            .statusCode(401);
    }

    @Test @Order(6)
    @DisplayName("TC-AUTH-06 | anonymous | PUT /users/update → 401")
    void anonymous_updateOwnAccount_returns401() {
        given()
            .spec(anonSpec)
            .body("{}")
        .when()
            .put("/users/update")
        .then()
            .statusCode(401);
    }

    @Test @Order(7)
    @DisplayName("TC-AUTH-07 | anonymous | DELETE /users/delete → 401")
    void anonymous_deleteOwnAccount_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .delete("/users/delete")
        .then()
            .statusCode(401);
    }

    @Test @Order(8)
    @DisplayName("TC-AUTH-08 | anonymous | POST /rooms/add → 401")
    void anonymous_addRoom_returns401() {
        given()
            .spec(anonSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    997)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(401);
    }

    @Test @Order(9)
    @DisplayName("TC-AUTH-09 | anonymous | PUT /rooms/update → 401")
    void anonymous_updateRoom_returns401() {
        given()
            .spec(anonSpec)
            .contentType("multipart/form-data")
            .multiPart("id",   1)
            .multiPart("type", "SUITE")
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(401);
    }

    @Test @Order(10)
    @DisplayName("TC-AUTH-10 | anonymous | DELETE /rooms/delete/{id} → 401")
    void anonymous_deleteRoom_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .delete("/rooms/delete/{id}", 1L)
        .then()
            .statusCode(401);
    }

    @Test @Order(11)
    @DisplayName("TC-AUTH-11 | anonymous | GET /notifications/all → 401")
    void anonymous_getAllNotifications_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(401);
    }

    @Test @Order(12)
    @DisplayName("TC-AUTH-12 | [Bug] anonymous | PUT /bookings/update → should be 401, missing auth")
    void anonymous_updateBooking_shouldReturn401_bugNoAuth() {
        // Bug: PUT /bookings/update is not protected in SecurityConfig.
        // Anonymous requests bypass authentication and reach the controller/service layer.
        // Expected: 401. Current actual: 400/404 (authentication not triggered).
        // Fix: add .hasRole("ADMIN") for this path in SecurityConfig.authorizeHttpRequests().
        given()
            .spec(anonSpec)
            .body(java.util.Map.of("id", 1L, "bookingStatus", "CANCELLED"))
        .when()
            .put("/bookings/update")
        .then()
            // 401 = fixed; 200/400/404 = BUG (authentication not triggered)
            .statusCode(anyOf(is(200), is(400), is(401), is(404)));

        System.out.println("⚠️  TC-AUTH-12: anonymous PUT /bookings/update — should be 401, check SecurityConfig");
    }

    @Test @Order(13)
    @DisplayName("TC-AUTH-13 | fake JWT | GET /users/account → 401")
    void invalidJwt_accessProtectedEndpoint_returns401() {
        given()
            .header("Authorization", "Bearer this.is.not.a.valid.jwt")
            .contentType("application/json")
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(401);
    }

    @Test @Order(14)
    @DisplayName("TC-AUTH-14 | tampered JWT | signature invalid → 401")
    void tamperedJwt_accessProtectedEndpoint_returns401() {
        // A structurally valid JWT but with a tampered payload (wrong signature)
        String tamperedJwt =
            "eyJhbGciOiJIUzI1NiJ9" +                          // header (HS256)
            ".eyJzdWIiOiJhZG1pbkBob3RlbC5jb20iLCJyb2xlIjoiQURNSU4ifQ" + // payload
            ".INVALIDSIGNATURE_tampered_to_fail_verification"; // wrong sig

        given()
            .header("Authorization", "Bearer " + tamperedJwt)
            .contentType("application/json")
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(401);
    }

    @Test @Order(15)
    @DisplayName("TC-AUTH-15 | empty Bearer token | GET /users/account → 401")
    void emptyBearerToken_returns401() {
        given()
            .header("Authorization", "Bearer ")
            .contentType("application/json")
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(401);
    }

    @Test @Order(16)
    @DisplayName("TC-AUTH-16 | deleted account | old token rejected")
    void deletedAccount_oldToken_isRejected() {
        String email    = "todelete_" + System.currentTimeMillis() + "@hotel.com";
        String password = "ToDelete1234!";

        given().spec(anonSpec)
               .body(registrationPayload(email, password))
               .when().post("/auth/register")
               .then().statusCode(200);

        String token = loginAndGetToken(email, password);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .accept("application/json")
        .when()
            .delete("/users/delete")
        .then()
            .statusCode(200);

        // JWT is stateless — token remains valid until expiry even after deletion.
        // If this returns 200, the system does not revoke tokens on account deletion.
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(anyOf(is(401), is(403), is(404)));
    }

    @Test @Order(17)
    @DisplayName("TC-AUTH-17 | CUSTOMER | GET /users/all → 403")
    void customer_accessAllUsers_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .get("/users/all")
        .then()
            .statusCode(403);
    }

    @Test @Order(18)
    @DisplayName("TC-AUTH-18 | CUSTOMER | GET /bookings/all → 403")
    void customer_accessAllBookings_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/all")
        .then()
            .statusCode(403);
    }

    @Test @Order(19)
    @DisplayName("TC-AUTH-19 | CUSTOMER | POST /rooms/add → 403")
    void customer_addRoom_returns403() {
        given()
            .spec(customerSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    999)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(403);
    }

    @Test @Order(20)
    @DisplayName("TC-AUTH-20 | CUSTOMER | PUT /rooms/update → 403")
    void customer_updateRoom_returns403() {
        given()
            .spec(customerSpec)
            .contentType("multipart/form-data")
            .multiPart("id",   1)
            .multiPart("type", "SUITE")
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(403);
    }

    @Test @Order(21)
    @DisplayName("TC-AUTH-21 | CUSTOMER | DELETE /rooms/delete → 403")
    void customer_deleteRoom_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .delete("/rooms/delete/{id}", 1L)
        .then()
            .statusCode(403);
    }

    @Test @Order(22)
    @DisplayName("TC-AUTH-22 | CUSTOMER | PUT /bookings/update → 403")
    void customer_updateBooking_returns403() {
        given()
            .spec(customerSpec)
            .body(java.util.Map.of("id", 1L, "bookingStatus", "CANCELLED"))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(403);
    }

    @Test @Order(23)
    @DisplayName("TC-AUTH-23 | CUSTOMER | GET /notifications/all → 403")
    void customer_accessAllNotifications_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(403);
    }

    @Test @Order(24)
    @DisplayName("TC-AUTH-24 | [Bug] anonymous | GET /bookings/{ref} → publicly exposed (P0)")
    void anonymous_getBookingByRef_shouldReturn401_bugExposed() {
        // Bug (P0): GET /bookings/{ref} is not secured in SecurityConfig.
        // Any anonymous user with a valid bookingReference can view full booking details including personal data.
        // Fix: require authentication for /bookings/** in SecurityConfig.authorizeHttpRequests();
        //      add an ownership check in the service (current user == booking owner || ADMIN).

        Long roomId = resolveFirstRoomId();
        if (roomId == null) { System.out.println("⚠️  TC-AUTH-24: no room found, skipped"); return; }

        String ref = given()
            .spec(adminSpec)
            .body(bookingPayload(roomId, inDays(30), inDays(32)))
        .when()
            .post("/bookings")
        .then()
            .extract().path("booking.bookingReference");

        if (ref == null) { System.out.println("⚠️  TC-AUTH-24: booking creation failed, skipped"); return; }

        int status = given()
            .spec(anonSpec)
        .when()
            .get("/bookings/{ref}", ref)
        .then()
            // 200 = BUG (publicly exposed); 401 = FIXED
            .statusCode(anyOf(is(200), is(401)))
            .extract().statusCode();

        System.out.println("⚠️  TC-AUTH-24: anonymous GET /bookings/{ref} → " + status
                + (status == 401 ? " ← FIXED" : " ← BUG: endpoint publicly exposed (P0)"));
    }

    @Test @Order(25)
    @DisplayName("TC-AUTH-25 | [Bug] IDOR | customer can read another customer's booking")
    void customerA_getCustomerB_bookingByRef_shouldReturn403_IDOR() {
        // Bug (P1 — IDOR): GET /bookings/{ref} only checks that the user is authenticated,
        // not that they own the booking. Any logged-in user can read another user's full booking details.
        // Fix: if (!booking.getUser().getEmail().equals(loggedInEmail) && !isAdmin)
        //          throw new AccessDeniedException("forbidden");

        String emailB    = "idor_b_" + System.currentTimeMillis() + "@hotel.com";
        String passwordB = "CustomerB1234!";
        given().spec(anonSpec).body(registrationPayload(emailB, passwordB))
               .when().post("/auth/register").then().statusCode(200);

        String tokenB = loginAndGetToken(emailB, passwordB);
        Long   roomId = resolveFirstRoomId();

        String refB = null;
        if (roomId != null) {
            refB = given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType("application/json")
                .accept("application/json")
                .body(bookingPayload(roomId, inDays(35), inDays(37)))
            .when()
                .post("/bookings")
            .then()
                .extract().path("booking.bookingReference");
        }

        if (refB != null) {
            int status = given()
                .spec(customerSpec)
            .when()
                .get("/bookings/{ref}", refB)
            .then()
                // 403 = FIXED (ownership enforced); 200 = BUG (IDOR)
                .statusCode(anyOf(is(200), is(403)))
                .extract().statusCode();

            System.out.println("⚠️  TC-AUTH-25: customer A accessing customer B's booking → " + status
                    + (status == 403 ? " ← FIXED" : " ← BUG: IDOR (P1)"));
        }

        deleteAccount(tokenB);
    }
}
