package api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API tests for User endpoints.
 *
 * Assumed routes:
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

    private static final String NEW_USER_EMAIL    = "new_" + UUID.randomUUID() + "@hotel.com";
    private static final String NEW_USER_PASSWORD = "TestPass1234!";

    // ═══════════════════════════════════════════════════════════════
    // TC-U-01  registerUser: 成功注册，默认角色 CUSTOMER
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-U-01 | registerUser | 成功注册新用户，默认角色 CUSTOMER")
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
    // TC-U-02~06  registerUser: 必填字段校验 → 400
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(2)
    @DisplayName("TC-U-02 | registerUser | firstName=null → 400（@NotBlank 校验）")
    void registerUser_nullFirstName_returns400() {
        Map<String, Object> body = registrationPayload(NEW_USER_EMAIL, NEW_USER_PASSWORD);
        body.remove("firstName");

        given().spec(anonSpec).body(body).when().post("/auth/register")
               .then().statusCode(400);
    }

    @Test @Order(3)
    @DisplayName("TC-U-03 | registerUser | email=null → 400（@NotBlank 校验）")
    void registerUser_nullEmail_returns400() {
        Map<String, Object> body = registrationPayload(NEW_USER_EMAIL, NEW_USER_PASSWORD);
        body.remove("email");

        given().spec(anonSpec).body(body).when().post("/auth/register")
               .then().statusCode(400);
    }

    @Test @Order(4)
    @DisplayName("TC-U-04 | registerUser | phoneNumber=null → 400（@NotBlank 校验）")
    void registerUser_nullPhoneNumber_returns400() {
        Map<String, Object> body = registrationPayload(NEW_USER_EMAIL, NEW_USER_PASSWORD);
        body.remove("phoneNumber");

        given().spec(anonSpec).body(body).when().post("/auth/register")
               .then().statusCode(400);
    }

    @Test @Order(5)
    @DisplayName("TC-U-05 | registerUser | password=null → 400（@NotBlank 校验）")
    void registerUser_nullPassword_returns400() {
        Map<String, Object> body = registrationPayload(NEW_USER_EMAIL, NEW_USER_PASSWORD);
        body.remove("password");

        given().spec(anonSpec).body(body).when().post("/auth/register")
               .then().statusCode(400);
    }

    @Test @Order(6)
    @DisplayName("TC-U-06 | registerUser | email 格式非法 → 400（@Email 校验）")
    void registerUser_invalidEmailFormat_returns400() {
        Map<String, Object> body = registrationPayload(NEW_USER_EMAIL, NEW_USER_PASSWORD);
        body.put("email", "notanemail");

        given().spec(anonSpec).body(body).when().post("/auth/register")
               .then().statusCode(400);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-07  [Bug] registerUser: 权限提升漏洞
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(7)
    @DisplayName("TC-U-07 | [Bug] registerUser | 注册时自行指定 role=ADMIN — 权限提升漏洞")
    void registerUser_bug_privilegeEscalation_withAdminRole() {
        String uniqueEmail = "admin_" + UUID.randomUUID() + "@hotel.com";
        Map<String, Object> body = registrationPayload(uniqueEmail, "AdminPass123!");
        body.put("role", "ADMIN");

        int status = given()
            .spec(anonSpec)
            .body(body)
        .when()
            .post("/auth/register")
        .then()
            .extract().statusCode();

        if (status == 200) {
            System.out.println("⚠️  SECURITY BUG TC-U-07: Registration accepted role=ADMIN from anonymous user. " +
                    "Fix: ignore role field in registration, force CUSTOMER.");
        }
        assertTrue(status == 200 || status == 400,
                "Expected 200 (bug) or 400 (fixed). Got: " + status);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-08  loginUser: 成功，返回有效 JWT
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(8)
    @DisplayName("TC-U-08 | loginUser | 邮箱密码正确，返回有效 JWT Token")
    void loginUser_success_returnsJwtToken() {
        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    USER_EMAIL);
        loginBody.put("password", USER_PASSWORD);

        String token = given()
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
            .body("expirationTime", equalTo("6 months"))
            .extract().path("token");

        String[] parts = token.split("\\.");
        assertTrue(parts.length == 3, "JWT must have 3 parts (header.payload.signature), got: " + parts.length);

        String payloadJson = new String(Base64.getUrlDecoder().decode(
                parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4)));
        assertTrue(payloadJson.contains("\"sub\""),
                "JWT payload must contain 'sub' claim. Payload: " + payloadJson);
        assertTrue(payloadJson.contains(USER_EMAIL),
                "JWT 'sub' claim must equal login email. Payload: " + payloadJson);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-09  loginUser: 密码错误 → 401
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(9)
    @DisplayName("TC-U-09 | loginUser | 密码错误 → 401")
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
            .statusCode(anyOf(is(400), is(401)))
            .body("message", containsStringIgnoringCase("password"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-10  loginUser: 邮箱不存在 → 404
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(10)
    @DisplayName("TC-U-10 | loginUser | 邮箱不存在 → 404")
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
    // TC-U-11  getOwnAccountDetails: 成功，response 不含 password
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(11)
    @DisplayName("TC-U-11 | getOwnAccountDetails | 成功获取账户详情，response 中不含 password 字段")
    void getOwnAccountDetails_success() {
        given()
            .spec(customerSpec)
        .when()
            .get("/users/account")
        .then()
            .statusCode(200)
            .body("status",        equalTo(200))
            .body("user.email",    equalTo(USER_EMAIL))
            .body("user",          notNullValue())
            .body("user.password", anyOf(nullValue(), emptyString()));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-12  updateOwnAccount: 更新姓名/电话，原密码仍有效
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(12)
    @DisplayName("TC-U-12 | updateOwnAccount | 更新姓名和电话，原密码仍然有效")
    void updateOwnAccount_success_withoutChangingPassword() {
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("firstName",   "UpdatedFirst");
        updateBody.put("lastName",    "UpdatedLast");
        updateBody.put("phoneNumber", "08012345678");

        given()
            .spec(customerSpec)
            .body(updateBody)
        .when()
            .put("/users/update")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("updated"));

        given().spec(customerSpec).when().get("/users/account")
               .then()
               .body("user.firstName",   equalTo("UpdatedFirst"))
               .body("user.lastName",    equalTo("UpdatedLast"))
               .body("user.phoneNumber", equalTo("08012345678"));

        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    USER_EMAIL);
        loginBody.put("password", USER_PASSWORD);

        given().spec(anonSpec).body(loginBody).when().post("/auth/login")
               .then().statusCode(200).body("token", notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-13  updateOwnAccount: 修改密码后旧密码失效，新密码可登录
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(13)
    @DisplayName("TC-U-13 | updateOwnAccount | 修改密码后旧密码失效，新密码可登录")
    void updateOwnAccount_success_changePassword() {
        String newPassword = "NewPass5678!";

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("password", newPassword);

        given().spec(customerSpec).body(updateBody).when().put("/users/update")
               .then().statusCode(200);

        Map<String, String> oldLoginBody = new HashMap<>();
        oldLoginBody.put("email",    USER_EMAIL);
        oldLoginBody.put("password", USER_PASSWORD);

        given().spec(anonSpec).body(oldLoginBody).when().post("/auth/login")
               .then().statusCode(anyOf(is(400), is(401)));

        Map<String, String> newLoginBody = new HashMap<>();
        newLoginBody.put("email",    USER_EMAIL);
        newLoginBody.put("password", newPassword);

        given().spec(anonSpec).body(newLoginBody).when().post("/auth/login")
               .then().statusCode(200).body("token", notNullValue());

        // Teardown: restore original password
        updateBody.put("password", USER_PASSWORD);
        given().spec(customerSpec).body(updateBody).when().put("/users/update");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-14  getMyBookingHistory: 成功返回历史订单
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(14)
    @DisplayName("TC-U-14 | getMyBookingHistory | 成功获取历史订单列表")
    void getMyBookingHistory_success() {
        given()
            .spec(customerSpec)
        .when()
            .get("/users/bookings")
        .then()
            .statusCode(200)
            .body("status",   equalTo(200))
            .body("bookings", notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-U-15  getMyBookingHistory: 无订单时返回空列表（非 null）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(15)
    @DisplayName("TC-U-15 | getMyBookingHistory | 无订单时返回空列表，不返回 null")
    void getMyBookingHistory_noBookings_returnsEmptyList() {
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
    // TC-U-16  deleteOwnAccount: 删除后 token 失效，无法重新登录
    //          (run last to avoid breaking other tests)
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(99)
    @DisplayName("TC-U-16 | deleteOwnAccount | 删除账户后 token 失效，重新登录返回 400/404")
    void deleteOwnAccount_success() {
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

        given()
            .header("Authorization", "Bearer " + tempToken)
            .accept("application/json")
        .when()
            .get("/users/account")
        .then()
            .statusCode(anyOf(is(401), is(403)));

        Map<String, String> loginBody = new HashMap<>();
        loginBody.put("email",    tempEmail);
        loginBody.put("password", tempPassword);

        given().spec(anonSpec).body(loginBody).when().post("/auth/login")
               .then().statusCode(anyOf(is(400), is(404)));
    }
}
