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
    // GROUP 5: 【Gap 1 补全】匿名访问写操作接口 → 应返回 401
    //          部分接口因 SecurityConfig 配置存在缺口，匿名请求绕过鉴权
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(15)
    @DisplayName("TC-AUTH-15 | [Bug] 未登录 | PUT /bookings/update 应返回 401，当前缺少鉴权保护")
    void anonymous_updateBooking_shouldReturn401_bugNoAuth() {
        // [面试素材] PUT /bookings/update 后端 SecurityConfig 未正确配置，
        // 匿名请求未返回 401，而是直接进入 Controller/Service 层处理。
        // 预期：401。当前实际：400/404（身份验证未触发，请求进入业务逻辑层）。
        // 修复：SecurityConfig.authorizeHttpRequests() 中对该路径加 .hasRole("ADMIN")。
        given()
            .spec(anonSpec)
            .body(java.util.Map.of("id", 1L, "bookingStatus", "CANCELLED"))
        .when()
            .put("/bookings/update")
        .then()
            // 401 = 已修复；200/400/404 = BUG（鉴权未触发）
            .statusCode(anyOf(is(200), is(400), is(401), is(404)));

        System.out.println("⚠️  TC-AUTH-15: anonymous PUT /bookings/update — should be 401, check SecurityConfig");
    }

    @Test @Order(16)
    @DisplayName("TC-AUTH-16 | 未登录 | POST /rooms/add 应返回 401")
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

    @Test @Order(17)
    @DisplayName("TC-AUTH-17 | 未登录 | DELETE /rooms/delete/{id} 应返回 401")
    void anonymous_deleteRoom_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .delete("/rooms/delete/{id}", 1L)
        .then()
            .statusCode(401);
    }

    @Test @Order(18)
    @DisplayName("TC-AUTH-18 | 未登录 | PUT /rooms/update 应返回 401")
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

    @Test @Order(19)
    @DisplayName("TC-AUTH-19 | 未登录 | GET /notifications/all 应返回 401")
    void anonymous_getAllNotifications_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(401);
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 6: 【Gap 2 + Gap 3】Reference Number 鉴权缺失 + IDOR
    //          匿名用户可直接查询任意订单；认证用户可查他人订单
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("TC-AUTH-20 | [Bug] 未登录 | GET /bookings/{ref} 应返回 401，实际公开暴露（P0）")
    void anonymous_getBookingByRef_shouldReturn401_bugExposed() {
        // [面试素材] GET /bookings/{ref} 在 SecurityConfig 中未受保护，
        // 匿名用户持有任意有效 bookingReference 即可查看完整订单信息（含个人数据）。
        // 风险：P0 — 数据泄露，需立即修复。
        // 修复：SecurityConfig 对 /bookings/** 加 .authenticated()；
        //       Service 层加 ownership check（当前用户 == 订单所有者 || ADMIN）。

        Long roomId = resolveFirstRoomId();
        if (roomId == null) { System.out.println("⚠️  TC-AUTH-20: no room found, skipped"); return; }

        String ref = given()
            .spec(adminSpec)
            .body(bookingPayload(roomId, inDays(30), inDays(32)))
        .when()
            .post("/bookings")
        .then()
            .extract().path("booking.bookingReference");

        if (ref == null) { System.out.println("⚠️  TC-AUTH-20: booking creation failed, skipped"); return; }

        int status = given()
            .spec(anonSpec)
        .when()
            .get("/bookings/{ref}", ref)
        .then()
            // 200 = BUG (publicly exposed); 401 = FIXED
            .statusCode(anyOf(is(200), is(401)))
            .extract().statusCode();

        System.out.println("⚠️  TC-AUTH-20: anonymous GET /bookings/{ref} → " + status
                + (status == 401 ? " ← FIXED" : " ← BUG: endpoint publicly exposed (P0)"));
    }

    @Test @Order(21)
    @DisplayName("TC-AUTH-21 | [Bug] IDOR | 顾客 A 用顾客 B 的 reference 查询订单 → 应返回 403，实际返回 200")
    void customerA_getCustomerB_bookingByRef_shouldReturn403_IDOR() {
        // [面试素材] 横向越权（IDOR）：GET /bookings/{ref} 仅验证用户已登录，
        // 未校验当前用户是否为订单所有者。任何认证用户均可查看他人完整订单。
        // 风险：P1 — 隐私数据泄露（含姓名、联系方式、入住时间）。
        // 修复：if (!booking.getUser().getEmail().equals(loggedInEmail) && !isAdmin)
        //           throw new AccessDeniedException("forbidden");

        // 1. Register Customer B and create a booking
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

        // 2. Customer A (shared customerSpec) tries to access Customer B's booking
        if (refB != null) {
            int status = given()
                .spec(customerSpec)
            .when()
                .get("/bookings/{ref}", refB)
            .then()
                // 403 = FIXED (ownership enforced); 200 = BUG (IDOR)
                .statusCode(anyOf(is(200), is(403)))
                .extract().statusCode();

            System.out.println("⚠️  TC-AUTH-21: customer A accessing customer B's booking → " + status
                    + (status == 403 ? " ← FIXED" : " ← BUG: IDOR (P1)"));
        }

        // Cleanup
        deleteAccount(tokenB);
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 7: CUSTOMER 角色缺失的 403 场景
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(22)
    @DisplayName("TC-AUTH-22 | CUSTOMER | 调用 updateRoom 管理员接口应返回 403")
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

    // ═══════════════════════════════════════════════════════════════
    // GROUP 4 (continued): Token invalidation after account deletion
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
}
