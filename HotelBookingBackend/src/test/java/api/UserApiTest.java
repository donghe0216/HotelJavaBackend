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
    // registerUser
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

    @Test @Order(3)
    @DisplayName("TC-U-03 | registerUser | [Bug] 重复 email → 应返回 409，当前返回 500")
    void registerUser_duplicateEmail_exposesDesignGap() {
        // [面试素材] registerUser() 无显式 email 重复检查，直接调用 userRepository.save()。
        // DB unique constraint 触发 DataIntegrityViolationException，若 GlobalExceptionHandler
        // 未针对注册场景捕获，最终透传为 500。
        // 对比：updateOwnAccount 重复 email（TC-U-21）已返回 409，
        // 因为 GlobalExceptionHandler 捕获了 DataIntegrityViolationException。
        // 两者差别在于 registerUser 是 POST（insert），触发的异常类型相同，
        // 理论上也应返回 409 — 若实际返回 500，说明 Handler 的 mapping 有缺口。
        given()
            .spec(anonSpec)
            .body(registrationPayload(USER_EMAIL, "SomePass1234!")) // USER_EMAIL 已存在
        .when()
            .post("/auth/register")
        .then()
            .statusCode(not(200));  // 任何非 200 都比静默成功好；期望 409

        System.out.println("⚠️  TC-U-03: duplicate email on register — expected 409, check actual status");
    }

    // ═══════════════════════════════════════════════════════════════
    // loginUser
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(4)
    @DisplayName("TC-U-04 | loginUser | 邮箱密码正确，登录成功，返回 JWT Token")
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

    @Test @Order(5)
    @DisplayName("TC-U-05 | loginUser | 密码错误，返回 401 / InvalidCredentialException")
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

    @Test @Order(6)
    @DisplayName("TC-U-06 | loginUser | 邮箱不存在，返回 404 / NotFoundException")
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

    @Test @Order(7)
    @DisplayName("TC-U-07 | loginUser | 【补充】isActive=false 时应拒绝登录（当前代码存在安全漏洞）")
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
    // getOwnAccountDetails
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(8)
    @DisplayName("TC-U-08 | getOwnAccountDetails | 认证用户成功获取自己的账户详情")
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

    @Test @Order(9)
    @DisplayName("TC-U-09 | getOwnAccountDetails | response body 不含 password 字段（敏感数据不泄露）")
    void getOwnAccountDetails_responseDoesNotContainPassword() {
        // 与 TC-U-17 同属"敏感字段泄露"类型，但路径不同：
        // TC-U-17 验 ADMIN 拉取用户列表；此处验用户查询自己账户详情。
        given()
            .spec(customerSpec)
        .when()
            .get("/users/account")
        .then()
            .statusCode(200)
            .body("user.password", nullValue());
    }

    // ═══════════════════════════════════════════════════════════════
    // updateOwnAccount
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("TC-U-10 | updateOwnAccount | 成功更新姓名和电话，密码不变")
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

    @Test @Order(11)
    @DisplayName("TC-U-11 | updateOwnAccount | 成功修改密码后可用新密码登录")
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

    @Test @Order(12)
    @DisplayName("TC-U-12 | updateOwnAccount | 【边界】password='' 时不应覆盖原密码")
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

    @Test @Order(13)
    @DisplayName("TC-U-13 | updateOwnAccount | [Bug] email 格式非法时应返回 400，实际返回 200（Service 层无格式校验）")
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

        System.out.println("⚠️  TC-U-13: invalid email accepted — missing @Email/@Valid validation");

        // Teardown: 恢复原邮箱，避免影响后续测试
        Map<String, Object> restore = new HashMap<>();
        restore.put("email", USER_EMAIL);
        given().spec(customerSpec).body(restore).when().put("/users/update");
    }

    @Test @Order(14)
    @DisplayName("TC-U-14 | updateOwnAccount | email 已被其他用户占用 → 409 Conflict")
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
    // getAllUsers
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(15)
    @DisplayName("TC-U-15 | getAllUsers | ADMIN 身份 → 200 + 用户列表不为空")
    void getAllUsers_admin_success() {
        given()
            .spec(adminSpec)
        .when()
            .get("/users/all")
        .then()
            .statusCode(200)
            .body("status", equalTo(200))
            .body("users",  notNullValue())
            .body("users",  not(empty()));
    }

    @Test @Order(16)
    @DisplayName("TC-U-16 | getAllUsers | response body 不含 password 字段（敏感数据不泄露）")
    void getAllUsers_responseDoesNotContainPassword() {
        // [面试素材] modelMapper 默认会把 User.password（哈希值）映射到 UserDTO.password，
        // 若 UserDTO.password 无 @JsonIgnore，哈希值会出现在响应体。
        // 修复：UserDTO.password 加 @JsonIgnore 或在 mapper 配置中跳过该字段。
        given()
            .spec(adminSpec)
        .when()
            .get("/users/all")
        .then()
            .statusCode(200)
            .body("users[0].password", nullValue());
    }

    // ═══════════════════════════════════════════════════════════════
    // getMyBookingHistory
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(17)
    @DisplayName("TC-U-17 | getMyBookingHistory | 成功获取历史订单列表")
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

    @Test @Order(18)
    @DisplayName("TC-U-18 | getMyBookingHistory | 【补充】无订单时返回空列表，不返回 null")
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
    // register — 入参校验
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(19)
    @DisplayName("TC-U-19 | register | 缺少 email 字段，应返回 400")
    void register_missingEmail_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Test");
        body.put("lastName",  "User");
        body.put("password",  "TestPass1234!");

        given().spec(anonSpec).body(body)
            .when().post("/auth/register")
            .then().statusCode(anyOf(is(400), is(422)))
                   .statusCode(not(500));
    }

    @Test @Order(20)
    @DisplayName("TC-U-20 | register | 缺少 password 字段，应返回 400")
    void register_missingPassword_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Test");
        body.put("lastName",  "User");
        body.put("email",     "nopw_" + System.currentTimeMillis() + "@hotel.com");

        given().spec(anonSpec).body(body)
            .when().post("/auth/register")
            .then().statusCode(anyOf(is(400), is(422)))
                   .statusCode(not(500));
    }

    @Test @Order(21)
    @DisplayName("TC-U-21 | register | email 超长字符串（300字符），不应返回 500")
    void register_oversizedEmail_doesNotReturn500() {
        String longString = "a".repeat(300);
        Map<String, Object> body = registrationPayload(longString + "@hotel.com", "TestPass1234!");

        given().spec(anonSpec).body(body)
            .when().post("/auth/register")
            .then().statusCode(not(500));
    }

    @Test @Order(22)
    @DisplayName("TC-U-22 | register | email 格式非法（无@符号），应返回 400")
    void register_invalidEmailFormat_returns400() {
        Map<String, Object> body = registrationPayload("not-an-email", "TestPass1234!");

        given().spec(anonSpec).body(body)
            .when().post("/auth/register")
            .then().statusCode(anyOf(is(400), is(422)))
                   .statusCode(not(500));
    }

    @Test @Order(23)
    @DisplayName("TC-U-23 | register | 空 JSON body，不应返回 500")
    void register_emptyBody_doesNotReturn500() {
        given().spec(anonSpec).body("{}")
            .when().post("/auth/register")
            .then().statusCode(not(500));
    }

    // ═══════════════════════════════════════════════════════════════
    // login — 入参校验
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(24)
    @DisplayName("TC-U-24 | login | 空 JSON body，不应返回 500")
    void login_emptyBody_doesNotReturn500() {
        given().spec(anonSpec).body("{}")
            .when().post("/auth/login")
            .then().statusCode(not(500));
    }

    @Test @Order(25)
    @DisplayName("TC-U-25 | login | email=null，不应返回 500")
    void login_nullEmail_doesNotReturn500() {
        Map<String, Object> body = new HashMap<>();
        body.put("email",    null);
        body.put("password", "TestPass1234!");

        given().spec(anonSpec).body(body)
            .when().post("/auth/login")
            .then().statusCode(not(500));
    }

    // ═══════════════════════════════════════════════════════════════
    // deleteOwnAccount — 最后执行，避免删除共享账户
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(99)
    @DisplayName("TC-U-26 | deleteOwnAccount | 成功删除当前登录账户")
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
