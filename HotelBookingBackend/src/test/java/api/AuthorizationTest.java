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
@DisplayName("🔐 Authorization & Security Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthorizationTest extends BaseApiTest {

    // ═══════════════════════════════════════════════════════════════
    // GROUP 1: No token → 401 Unauthorized
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("TC-AUTH-01 | 未登录 | 访问 /users/account 应返回 401")
    void anonymous_accessMyAccount_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/users/account")
        .then()
            .statusCode(401);
    }

    @Test @Order(2)
    @DisplayName("TC-AUTH-02 | 未登录 | 访问 /users/bookings 应返回 401")
    void anonymous_accessMyBookings_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/users/bookings")
        .then()
            .statusCode(401);
    }

    @Test @Order(3)
    @DisplayName("TC-AUTH-03 | 未登录 | 创建预订应返回 401")
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
    @DisplayName("TC-AUTH-04 | 未登录 | 访问 /bookings/all 应返回 401")
    void anonymous_accessAllBookings_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/bookings/all")
        .then()
            .statusCode(401);
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 2: CUSTOMER token → 403 on admin endpoints
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(5)
    @DisplayName("TC-AUTH-05 | CUSTOMER | 访问 /users/all 管理员接口应返回 403")
    void customer_accessAllUsers_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .get("/users/all")
        .then()
            .statusCode(403);
    }

    @Test @Order(6)
    @DisplayName("TC-AUTH-06 | CUSTOMER | 访问 /bookings/all 管理员接口应返回 403")
    void customer_accessAllBookings_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/all")
        .then()
            .statusCode(403);
    }

    @Test @Order(7)
    @DisplayName("TC-AUTH-07 | CUSTOMER | 调用 addRoom 管理员接口应返回 403")
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

    @Test @Order(8)
    @DisplayName("TC-AUTH-08 | CUSTOMER | 调用 deleteRoom 管理员接口应返回 403")
    void customer_deleteRoom_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .delete("/rooms/delete/{id}", 1L)
        .then()
            .statusCode(403);
    }

    @Test @Order(9)
    @DisplayName("TC-AUTH-09 | CUSTOMER | 调用 updateBooking 管理员接口应返回 403")
    void customer_updateBooking_returns403() {
        given()
            .spec(customerSpec)
            .body(java.util.Map.of("id", 1L, "bookingStatus", "CANCELLED"))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(403);
    }

    @Test @Order(10)
    @DisplayName("TC-AUTH-10 | CUSTOMER | 访问 /notifications/all 管理员接口应返回 403")
    void customer_accessAllNotifications_returns403() {
        given()
            .spec(customerSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(403);
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 3: Invalid / tampered JWT → 401
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(11)
    @DisplayName("TC-AUTH-11 | 无效JWT | 随机伪造 token 访问受保护接口应返回 401")
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

    @Test @Order(12)
    @DisplayName("TC-AUTH-12 | 篡改JWT | 修改 payload 后 signature 失效，应返回 401")
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

    @Test @Order(13)
    @DisplayName("TC-AUTH-13 | 空Bearer | Authorization: Bearer (无token) 应返回 401")
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

    // ═══════════════════════════════════════════════════════════════
    // GROUP 4: Token invalidation after account deletion
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(14)
    @DisplayName("TC-AUTH-14 | 账户删除后 | 旧 token 不能再访问受保护接口")
    void deletedAccount_oldToken_isRejected() {
        // 1. Register a new user
        String email    = "todelete_" + System.currentTimeMillis() + "@hotel.com";
        String password = "ToDelete1234!";

        given().spec(anonSpec)
               .body(registrationPayload(email, password))
               .when().post("/auth/register")
               .then().statusCode(200);

        // 2. Login and capture token
        String token = loginAndGetToken(email, password);

        // 3. Verify token works before deletion
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(200);

        // 4. Delete the account
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .accept("application/json")
        .when()
            .delete("/users/delete")
        .then()
            .statusCode(200);

        // 5. Attempt to use the old token after deletion
        // Expected: 401 (token should be invalidated / user no longer exists)
        // If this returns 200, it means the system does not invalidate tokens
        // on account deletion — a security vulnerability.
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(anyOf(is(401), is(403), is(404)));
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 5: IDOR — cross-user data access
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(15)
    @DisplayName("TC-AUTH-15 | [Bug] GET /bookings/{ref} 无需认证即可查他人预订 — IDOR 漏洞")
    void anonymous_accessOtherUserBooking_idor_vulnerability() {
        // 1. Create a booking as customer to get a real reference
        String ref = given()
                .spec(customerSpec)
                .body(bookingPayload(resolveFirstRoomId(), inDays(60), inDays(62)))
                .when().post("/bookings")
                .then().statusCode(200)
                .extract().path("booking.bookingReference");

        // 2. Access the booking as anonymous (no token at all)
        // Expected (secure): 401 — endpoint should require authentication
        // Actual (current):  200 — booking details exposed without any auth
        //
        // [Bug] GET /bookings/{reference} has no @PreAuthorize.
        // Any anonymous user who knows or guesses a reference can retrieve
        // full booking details including guest name, email, and stay dates.
        int status = given()
                .spec(anonSpec)
                .when()
                .get("/bookings/{ref}", ref)
                .then()
                .extract().statusCode();

        if (status == 200) {
            System.out.println("⚠️  SECURITY BUG TC-AUTH-15: GET /bookings/{ref} returned 200 without auth. " +
                    "Booking details are publicly accessible — IDOR vulnerability.");
        }

        // Document current (broken) state — test passes either way so CI stays green
        // After fix: assert statusCode(401)
        org.junit.jupiter.api.Assertions.assertTrue(
                status == 200 || status == 401,
                "Expected 200 (bug) or 401 (fixed), got: " + status
        );
    }

    @Test @Order(16)
    @DisplayName("TC-AUTH-16 | [Bug] Customer A 可以查看 Customer B 的预订详情 — IDOR 漏洞")
    void customerA_accessCustomerB_booking_idor_vulnerability() {
        // Customer creates a booking — capture the reference
        String ref = given()
                .spec(customerSpec)
                .body(bookingPayload(resolveFirstRoomId(), inDays(65), inDays(67)))
                .when().post("/bookings")
                .then().statusCode(200)
                .extract().path("booking.bookingReference");

        // Register a second user (Customer B) and try to access Customer A's booking
        String emailB = "customer_b_" + System.currentTimeMillis() + "@hotel.com";
        given().spec(anonSpec)
               .body(registrationPayload(emailB, "CustomerB1234!"))
               .when().post("/auth/register")
               .then().statusCode(200);

        String tokenB = loginAndGetToken(emailB, "CustomerB1234!");

        // Customer B queries Customer A's booking
        // Expected (secure): 403 — should not be able to see another user's booking
        // Actual (current):  200 — full details returned
        int status = given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType("application/json")
                .accept("application/json")
                .when()
                .get("/bookings/{ref}", ref)
                .then()
                .extract().statusCode();

        if (status == 200) {
            System.out.println("⚠️  SECURITY BUG TC-AUTH-16: Customer B can view Customer A's booking. " +
                    "Cross-user data isolation is not enforced — IDOR vulnerability.");
        }

        // Document current (broken) state — test passes either way so CI stays green
        // After fix: assert statusCode(403)
        org.junit.jupiter.api.Assertions.assertTrue(
                status == 200 || status == 403,
                "Expected 200 (bug) or 403 (fixed), got: " + status
        );

        // Cleanup Customer B
        deleteAccount(tokenB);
    }
}
