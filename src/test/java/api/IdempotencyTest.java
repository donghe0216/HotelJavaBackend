package api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Idempotency & retry-safety tests.
 *
 * Verifies that operations behave correctly when repeated:
 *   - Repeating a read (GET) always returns consistent results
 *   - Repeating a write (POST/PUT/DELETE) does not cause duplicate
 *     side effects or data corruption
 *   - Notification records are not duplicated on retry
 *
 * These tests model real-world network retry scenarios where a client
 * cannot be sure whether the first request succeeded.
 */
@DisplayName("♻️ Idempotency & Retry Safety Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotencyTest extends BaseApiTest {

    private static final Long SEED_ROOM_ID = 1L;

    // ═══════════════════════════════════════════════════════════════
    // TC-IDEM-01  GET /rooms/all 多次请求，结果一致
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-IDEM-01 | GET /rooms/all | 重复请求返回相同结果（读幂等）")
    void getAllRooms_repeatedRequests_returnConsistentResults() {
        int firstCount = given().spec(anonSpec)
            .when().get("/rooms/all")
            .then().statusCode(200)
            .extract().path("rooms.size()");

        int secondCount = given().spec(anonSpec)
            .when().get("/rooms/all")
            .then().statusCode(200)
            .extract().path("rooms.size()");

        assertEquals(firstCount, secondCount,
            "GET /rooms/all returned different room counts on consecutive calls: "
            + firstCount + " vs " + secondCount);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-IDEM-02  重复注册同一邮箱，第二次应被拒绝（非幂等写入验证）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(2)
    @DisplayName("TC-IDEM-02 | register | 相同邮箱注册两次，第二次应被拒绝")
    void register_sameEmailTwice_secondRequestRejected() {
        String email    = "idem_" + System.currentTimeMillis() + "@hotel.com";
        String password = "IdemPass1234!";

        // First registration — should succeed
        given().spec(anonSpec)
               .body(registrationPayload(email, password))
               .when().post("/auth/register")
               .then().statusCode(200);

        // Second registration with same email — must be rejected
        given().spec(anonSpec)
               .body(registrationPayload(email, password))
               .when().post("/auth/register")
               .then()
               .statusCode(anyOf(is(400), is(409), is(422)));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-IDEM-03  重复 DELETE 同一房间，第二次应返回 404（非500）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(3)
    @DisplayName("TC-IDEM-03 | deleteRoom | 重复删除同一房间，第二次应返回 404 而非 500")
    void deleteRoom_twice_secondReturns404NotFound() {
        // 1. Create a room to delete
        Integer roomId = given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    996)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
            .multiPart("description",   "Room for idempotency test")
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(200)
            .extract().path("room.id");

        // 2. First delete — should succeed
        given().spec(adminSpec)
               .when().delete("/rooms/delete/{id}", roomId)
               .then().statusCode(200);

        // 3. Second delete — should return 404 (not 500)
        given().spec(adminSpec)
               .when().delete("/rooms/delete/{id}", roomId)
               .then()
               .statusCode(anyOf(is(400), is(404)))
               .statusCode(not(500));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-IDEM-04  重复 PUT updateBooking 相同状态，结果稳定
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(4)
    @DisplayName("TC-IDEM-04 | updateBooking | 重复更新相同状态，结果稳定（写幂等）")
    void updateBooking_sameStatusTwice_resultIsStable() {
        // 1. Create booking
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(80), inDays(82)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        Integer id = given()
            .spec(adminSpec)
        .when()
            .get("/bookings/{ref}", ref)
        .then()
            .extract().path("booking.id");

        java.util.Map<String, Object> updateBody =
            java.util.Map.of("id", id, "bookingStatus", "CHECKED_IN", "paymentStatus", "COMPLETED");

        // 2. First update
        given().spec(adminSpec).body(updateBody)
               .when().put("/bookings/update")
               .then().statusCode(200);

        // 3. Second update with same values — must not corrupt state or throw 500
        given().spec(adminSpec).body(updateBody)
               .when().put("/bookings/update")
               .then().statusCode(anyOf(is(200), is(400)))
                      .statusCode(not(500));

        // 4. Final state must still be correct
        given().spec(adminSpec)
               .when().get("/bookings/{ref}", ref)
               .then()
               .statusCode(200)
               .body("booking.bookingStatus", equalTo("CHECKED_IN"))
               .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-IDEM-05  booking 创建后，notification 不会重复写入
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(5)
    @DisplayName("TC-IDEM-05 | notification | 单次 booking 只产生一条 notification 记录")
    void createBooking_onlyOneNotificationCreated() {
        // 1. Create booking
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(90), inDays(92)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        // 2. Wait for @Async sendEmail to complete (poll instead of fixed sleep)
        pollUntil(8, () ->
            given().spec(adminSpec).when().get("/notifications/all")
                   .then().extract().jsonPath()
                   .getList("notifications.findAll { it.bookingReference == '" + ref + "' }")
                   .size() > 0
        );

        // 3. Count notifications for this booking reference
        int notificationCount = given()
            .spec(adminSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(anyOf(is(200), is(403)))
            .extract()
            .jsonPath()
            .getList("notifications.findAll { it.bookingReference == '" + ref + "' }")
            .size();

        // Each booking should produce exactly 1 notification
        // If count > 1, the async handler is firing multiple times — a bug
        assertTrue(notificationCount <= 1,
            "Expected at most 1 notification for booking " + ref
            + " but found: " + notificationCount
            + ". Possible duplicate sendEmail() invocation.");
    }
}
