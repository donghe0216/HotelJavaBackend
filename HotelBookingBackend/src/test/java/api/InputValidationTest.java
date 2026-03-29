package api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Input validation & contract tests.
 *
 * Verifies that the API correctly handles dirty, malformed, and
 * boundary inputs without returning 500 Internal Server Error.
 *
 * The contract being tested:
 *   - Missing required fields  → 400
 *   - Null values              → 400
 *   - Oversized strings        → 400 (not 500)
 *   - Invalid enum values      → 400
 *   - Wrong type (string for number) → 400
 *   - Special characters       → handled gracefully
 *
 * A 500 on any of these is always a bug.
 */
@DisplayName("📋 Input Validation & Contract Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InputValidationTest extends BaseApiTest {

    private static final String LONG_STRING = "a".repeat(300);

    // ═══════════════════════════════════════════════════════════════
    // GROUP 1: Register — missing / null / oversized fields
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("TC-IV-01 | register | 缺少 email 字段，应返回 400")
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

    @Test @Order(2)
    @DisplayName("TC-IV-02 | register | 缺少 password 字段，应返回 400")
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

    @Test @Order(3)
    @DisplayName("TC-IV-03 | register | email 超长字符串（300字符），不应返回 500")
    void register_oversizedEmail_doesNotReturn500() {
        Map<String, Object> body = registrationPayload(LONG_STRING + "@hotel.com", "TestPass1234!");

        given().spec(anonSpec).body(body)
            .when().post("/auth/register")
            .then().statusCode(not(500));
    }

    @Test @Order(4)
    @DisplayName("TC-IV-04 | register | email 格式非法（无@符号），应返回 400")
    void register_invalidEmailFormat_returns400() {
        Map<String, Object> body = registrationPayload("not-an-email", "TestPass1234!");

        given().spec(anonSpec).body(body)
            .when().post("/auth/register")
            .then().statusCode(anyOf(is(400), is(422)))
                   .statusCode(not(500));
    }

    @Test @Order(5)
    @DisplayName("TC-IV-05 | register | 空 JSON body，不应返回 500")
    void register_emptyBody_doesNotReturn500() {
        given().spec(anonSpec).body("{}")
            .when().post("/auth/register")
            .then().statusCode(not(500));
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 2: Login — malformed inputs
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(6)
    @DisplayName("TC-IV-06 | login | 空 JSON body，不应返回 500")
    void login_emptyBody_doesNotReturn500() {
        given().spec(anonSpec).body("{}")
            .when().post("/auth/login")
            .then().statusCode(not(500));
    }

    @Test @Order(7)
    @DisplayName("TC-IV-07 | login | email=null，不应返回 500")
    void login_nullEmail_doesNotReturn500() {
        Map<String, Object> body = new HashMap<>();
        body.put("email",    null);
        body.put("password", "TestPass1234!");

        given().spec(anonSpec).body(body)
            .when().post("/auth/login")
            .then().statusCode(not(500));
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 3: createBooking — missing / invalid fields
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(8)
    @DisplayName("TC-IV-08 | createBooking | 缺少 roomId，不应返回 500")
    void createBooking_missingRoomId_doesNotReturn500() {
        Map<String, Object> body = new HashMap<>();
        body.put("checkInDate",  inDays(5));
        body.put("checkOutDate", inDays(7));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then().statusCode(not(500));
    }

    @Test @Order(9)
    @DisplayName("TC-IV-09 | createBooking | 缺少 checkInDate，不应返回 500")
    void createBooking_missingCheckIn_doesNotReturn500() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       1L);
        body.put("checkOutDate", inDays(7));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then().statusCode(not(500));
    }

    @Test @Order(10)
    @DisplayName("TC-IV-10 | createBooking | 日期格式非法（非 yyyy-MM-dd），不应返回 500")
    void createBooking_invalidDateFormat_doesNotReturn500() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       1L);
        body.put("checkInDate",  "not-a-date");
        body.put("checkOutDate", "also-not-a-date");

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then().statusCode(not(500));
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 4: addRoom — invalid enum / wrong types
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(11)
    @DisplayName("TC-IV-11 | addRoom | type 为非法枚举值，应返回 400")
    void addRoom_invalidRoomTypeEnum_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    500)
            .multiPart("type",          "PENTHOUSE")   // does not exist in RoomType enum
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(anyOf(is(400), is(422)))
            .statusCode(not(500));
    }

    @Test @Order(12)
    @DisplayName("TC-IV-12 | addRoom | pricePerNight 传字符串，不应返回 500")
    void addRoom_priceAsString_doesNotReturn500() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    501)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "not-a-number")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(not(500));
    }

    @Test @Order(13)
    @DisplayName("TC-IV-13 | addRoom | pricePerNight 为负数，不应返回 500")
    void addRoom_negativePrice_doesNotReturn500() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    502)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "-100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(not(500));
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUP 5: searchRoom — special characters / edge inputs
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(14)
    @DisplayName("TC-IV-14 | searchRoom | 特殊字符输入，不应返回 500")
    void searchRoom_specialCharacters_doesNotReturn500() {
        given()
            .spec(anonSpec)
            .queryParam("input", "' OR 1=1; --")   // SQL injection attempt
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(not(500));
    }

    @Test @Order(15)
    @DisplayName("TC-IV-15 | searchRoom | 超长关键词（300字符），不应返回 500")
    void searchRoom_oversizedInput_doesNotReturn500() {
        given()
            .spec(anonSpec)
            .queryParam("input", LONG_STRING)
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(not(500));
    }

    @Test @Order(16)
    @DisplayName("TC-IV-16 | searchRoom | Unicode / 日语字符输入，不应返回 500")
    void searchRoom_unicodeInput_doesNotReturn500() {
        given()
            .spec(anonSpec)
            .queryParam("input", "シングルルーム")
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(not(500));
    }
}
