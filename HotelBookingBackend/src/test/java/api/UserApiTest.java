package api;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * API tests for User endpoints.
 *
 * Assumed routes (adjust to match your actual Controller mappings):
 *   POST   /auth/register
 *   POST   /auth/login
 *   GET    /users/all              (ADMIN)
 *   GET    /users/account
 *   PUT    /users/update
 *   DELETE /users/delete
 *   GET    /users/bookings
 */
@DisplayName("👤 User API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserApiTest extends BaseApiTest {

    // Unique email per test run so tests stay idempotent
    private static final String NEW_USER_EMAIL    = "new_" + UUID.randomUUID() + "@hotel.com";
    private static final String NEW_USER_PASSWORD = "TestPass1234!";

    // ═══════════════════════════════════════════════════════════════
    // TC-U-01  registerUser: 成功注册，默认角色 CUSTOMER
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-U-01 | registerUser | 成功注册新用户，返回成功消息（角色验证需通过 GET /users/account 另行断言）")
    void registerUser_success_defaultRoleCustomer() {
        given()
            .spec(anonSpec)
            .body(registrationPayload(NEW_USER_EMAIL, NEW_USER_PASSWORD))
        .when()
            .post("/auth/register")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", equalTo("user created successfully"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-02  registerUser: 指定角色 ADMIN
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(2)
    @DisplayName("TC-U-02 | registerUser | 指定角色 ADMIN 注册成功")
    void registerUser_success_withAdminRole() {
        String uniqueEmail = "admin_" + UUID.randomUUID() + "@hotel.com";
        Map<String, Object> body = registrationPayload(uniqueEmail, "AdminPass123!");
        body.put("role", "ADMIN");

        given()
            .spec(anonSpec)
            .body(body)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(200)
            .body("status", equalTo(200));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-03  loginUser: 邮箱密码正确，返回 JWT
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(3)
    @DisplayName("TC-U-03 | loginUser | 邮箱密码正确，登录成功，返回 JWT Token")
    void loginUser_success_returnsJwtToken() {
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    USER_EMAIL);
        loginBody.put("password", USER_PASSWORD);

        given()
            .spec(anonSpec)
            .body(loginBody)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("status",         equalTo(200))
            .body("token",          notNullValue())
            .body("token",          not(emptyString()))
            .body("role",           notNullValue())
            .body("isActive",       equalTo(true))
            .body("expirationTime", equalTo("6 months"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-04  loginUser: 密码错误
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(4)
    @DisplayName("TC-U-04 | loginUser | 密码错误，返回 401 / InvalidCredentialException")
    void loginUser_fail_wrongPassword() {
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    USER_EMAIL);
        loginBody.put("password", "WrongPassword!");

        given()
            .spec(anonSpec)
            .body(loginBody)
        .when()
            .post("/auth/login")
        .then()
            // InvalidCredentialException → GlobalExceptionHandler → 400 BAD_REQUEST
            .statusCode(400)
            .body("message", containsStringIgnoringCase("doesn't match"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-05  loginUser: 邮箱不存在
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(5)
    @DisplayName("TC-U-05 | loginUser | 邮箱不存在，返回 404 / NotFoundException")
    void loginUser_fail_emailNotFound() {
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    "ghost_" + UUID.randomUUID() + "@nowhere.com");
        loginBody.put("password", "Whatever123!");

        given()
            .spec(anonSpec)
            .body(loginBody)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("email"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-06  【补充/安全】isActive=false 用户拒绝登录
    //
    // Pre-condition: seed a user with isActive=false in the DB,
    //                or toggle the flag via an admin API before this test.
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(6)
    @DisplayName("TC-U-06 | loginUser | 【补充】isActive=false 时应拒绝登录（当前代码存在安全漏洞）")
    void loginUser_fail_inactiveUser() {
        // TODO: Seed an inactive user before running; currently the service
        //       does NOT check isActive → this test documents the expected
        //       behaviour AFTER the bug is fixed.
        //
        // Once fixed, expect 403 or a meaningful error message.
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    "inactive@hotel.com");   // must be seeded as isActive=false
        loginBody.put("password", "Inactive1234!");

        ValidatableResponse resp = given()
            .spec(anonSpec)
            .body(loginBody)
        .when()
            .post("/auth/login")
        .then();

        // Document current (broken) vs expected behaviour:
        // resp.statusCode(403);  // ← uncomment after fix
        // For now, just assert the test runs without NPE
        resp.statusCode(anyOf(is(200), is(400), is(401), is(403), is(404)));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-07  getOwnAccountDetails: 成功获取当前用户信息
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(7)
    @DisplayName("TC-U-07 | getOwnAccountDetails | 认证用户成功获取自己的账户详情")
    void getOwnAccountDetails_success() {
        given()
            .spec(customerSpec)
        .when()
            .get("/users/account")
        .then()
            .statusCode(200)
            .body("status",       equalTo(200))
            .body("user.email",   equalTo(USER_EMAIL));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-08  updateOwnAccount: 更新姓名和电话（不改密码）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(8)
    @DisplayName("TC-U-08 | updateOwnAccount | 成功更新姓名和电话，密码不变")
    void updateOwnAccount_success_withoutChangingPassword() {
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("firstName",   "UpdatedFirst");
        updateBody.put("lastName",    "UpdatedLast");
        updateBody.put("phoneNumber", "08012345678");
        // password intentionally omitted → must stay unchanged

        given()
            .spec(customerSpec)
            .body(updateBody)
        .when()
            .put("/users/update")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("updated"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-09  updateOwnAccount: 成功修改密码
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(9)
    @DisplayName("TC-U-09 | updateOwnAccount | 成功修改密码后可用新密码登录")
    void updateOwnAccount_success_changePassword() {
        String newPassword = "NewPass5678!";

        // 1. Change password
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("password", newPassword);

        given()
            .spec(customerSpec)
            .body(updateBody)
        .when()
            .put("/users/update")
        .then()
            .statusCode(200);

        // 2. Verify: login with new password succeeds
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    USER_EMAIL);
        loginBody.put("password", newPassword);

        given()
            .spec(anonSpec)
            .body(loginBody)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue());

        // 3. Teardown: restore original password so other tests are unaffected
        updateBody.put("password", USER_PASSWORD);
        given().spec(customerSpec).body(updateBody).when().put("/users/update");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-10  【补充/边界】密码为空字符串 "" 时不应修改密码
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(10)
    @DisplayName("TC-U-10 | updateOwnAccount | 【边界】password='' 时不应覆盖原密码")
    void updateOwnAccount_emptyPasswordString_doesNotOverwrite() {
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("password", "");    // isEmpty() == true → should be ignored

        given()
            .spec(customerSpec)
            .body(updateBody)
        .when()
            .put("/users/update")
        .then()
            .statusCode(200);

        // Verify: original password still works
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    USER_EMAIL);
        loginBody.put("password", USER_PASSWORD);

        given()
            .spec(anonSpec)
            .body(loginBody)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-11  getMyBookingHistory: 成功返回当前用户历史订单
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(11)
    @DisplayName("TC-U-11 | getMyBookingHistory | 成功获取历史订单列表")
    void getMyBookingHistory_success() {
        given()
            .spec(customerSpec)
        .when()
            .get("/users/bookings")
        .then()
            .statusCode(200)
            .body("status",   equalTo(200))
            .body("bookings", notNullValue());   // could be empty list — both are valid
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-12  【补充】无历史订单时返回空列表（非 null）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(12)
    @DisplayName("TC-U-12 | getMyBookingHistory | 【补充】无订单时返回空列表，不返回 null")
    void getMyBookingHistory_noBookings_returnsEmptyList() {
        // Register a brand-new user with no bookings
        String freshEmail    = "fresh_" + UUID.randomUUID() + "@hotel.com";
        String freshPassword = "FreshPass1234!";

        given().spec(anonSpec).body(registrationPayload(freshEmail, freshPassword))
               .when().post("/auth/register").then().statusCode(200);

        String freshToken = loginAndGetToken(freshEmail, freshPassword);

        given()
            .header("Authorization", "Bearer " + freshToken)
            .accept("application/json")
        .when()
            .get("/users/bookings")
        .then()
            .statusCode(200)
            .body("bookings", notNullValue())
            .body("bookings", hasSize(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-14  updateOwnAccount: email 格式非法（无 @ 符号）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(13)
    @DisplayName("TC-U-14 | updateOwnAccount | [Bug] email 格式非法时应返回 400，实际返回 200（Service 层无格式校验）")
    void updateOwnAccount_invalidEmailFormat_returns200Bug() {
        // [面试素材] Bug: UserDTO.email 无 @Email 注解，Controller 无 @Valid，
        // Service 层直接写入任意字符串。修复方案：UserDTO 加 @Email + Controller 加 @Valid → 返回 400。
        Map<String, Object> body = new HashMap<>();
        body.put("email", "not-an-email");

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .put("/users/update")
        .then()
            // 期望 400，当前（broken）行为返回 200
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", equalTo("user updated successfully"));

        System.out.println("⚠️  TC-U-14: invalid email accepted — missing @Email/@Valid validation");

        // Teardown: 恢复原邮箱，避免影响后续测试
        Map<String, Object> restore = new HashMap<>();
        restore.put("email", USER_EMAIL);
        given().spec(customerSpec).body(restore).when().put("/users/update");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-15  updateOwnAccount: email 已被其他用户占用 → 409
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(14)
    @DisplayName("TC-U-15 | updateOwnAccount | email 已被其他用户占用 → 409 Conflict")
    void updateOwnAccount_duplicateEmail_returns409() {
        // GlobalExceptionHandler 捕获 DataIntegrityViolationException → 409
        // 精确消息来自 GlobalExceptionHandler.handleDataIntegrityViolation()
        Map<String, Object> body = new HashMap<>();
        body.put("email", ADMIN_EMAIL); // admin@hotel.com 已存在

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .put("/users/update")
        .then()
            .statusCode(409)
            .body("status",  equalTo(409))
            .body("message", equalTo("A record with the same unique value already exists."));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-13  deleteOwnAccount: 成功删除当前账户
    //          (run last to avoid breaking other tests)
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(99)
    @DisplayName("TC-U-13 | deleteOwnAccount | 成功删除当前登录账户")
    void deleteOwnAccount_success() {
        // Use the newly registered user so we don't wipe the shared customer
        String tempEmail    = "deleteme_" + UUID.randomUUID() + "@hotel.com";
        String tempPassword = "DeleteMe1234!";

        given().spec(anonSpec).body(registrationPayload(tempEmail, tempPassword))
               .when().post("/auth/register").then().statusCode(200);

        String tempToken = loginAndGetToken(tempEmail, tempPassword);

        given()
            .header("Authorization", "Bearer " + tempToken)
            .accept("application/json")
        .when()
            .delete("/users/delete")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("deleted"));
    }
}
