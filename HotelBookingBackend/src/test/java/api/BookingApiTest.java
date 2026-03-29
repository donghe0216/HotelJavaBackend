package api;

import org.junit.jupiter.api.Assumptions;
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
 * API tests for Booking endpoints.
 *
 * Assumed routes:
 *   POST   /bookings                       (CUSTOMER / authenticated)
 *   GET    /bookings/all                   (ADMIN)
 *   GET    /bookings/{ref}
 *   PUT    /bookings/update                (ADMIN)
 *
 * Pre-condition:
 *   A room with roomId=SEED_ROOM_ID must exist in the DB before running.
 *
 * Design notes:
 *   - Date validation and availability logic are covered by BookingServiceImplTest (unit).
 *   - Price calculation precision is covered by BookingServiceImplTest (unit).
 *   - Cross-user access (IDOR) is covered by AuthorizationTest.
 *   - This file focuses on end-to-end HTTP contract and state transition across the booking lifecycle.
 */
@DisplayName("📅 Booking API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingApiTest extends BaseApiTest {

    private static final Long SEED_ROOM_ID = 1L;

    private static String createdBookingRef;
    private static Long   createdBookingId;

    // ═══════════════════════════════════════════════════════════════
    // TC-B-01  createBooking: happy path
    //          验证端到端链路：HTTP 200, 初始状态字段, bookingReference 格式, 日期回显
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-B-01 | createBooking | 成功创建 1 晚订单，验证所有初始字段")
    void createBooking_success() {
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, tomorrow(), inDays(2));

        createdBookingRef = given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("success"))
            .body("booking", notNullValue())
            .body("booking.bookingStatus",    equalTo("BOOKED"))
            .body("booking.paymentStatus",    equalTo("PENDING"))
            .body("booking.bookingReference", notNullValue())
            .body("booking.bookingReference", matchesRegex("[A-Z1-9]{10}"))
            .body("booking.totalPrice",       notNullValue())
            .body("booking.checkInDate",      equalTo(tomorrow()))
            .body("booking.checkOutDate",     equalTo(inDays(2)))
            .extract().path("booking.bookingReference");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-02  createBooking: 房间不存在 → NotFoundException
    //          验证 HTTP 层对外键不合法的响应契约
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(2)
    @DisplayName("TC-B-02 | createBooking | 房间 ID 不存在，抛出 NotFoundException")
    void createBooking_fail_roomNotFound() {
        Map<String, Object> body = bookingPayload(999999L, tomorrow(), inDays(3));

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("room"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-03  getAllBookings: ADMIN 获取全部订单
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(3)
    @DisplayName("TC-B-03 | getAllBookings | 返回列表中每条记录的 user 和 room 均为 null")
    void getAllBookings_bookingListHasNullUserAndRoom() {
        given()
            .spec(adminSpec)
        .when()
            .get("/bookings/all")
        .then()
            .statusCode(200)
            .body("status",   equalTo(200))
            .body("bookings", not(empty()))
            .body("bookings.user", everyItem(nullValue()))
            .body("bookings.room", everyItem(nullValue()));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-04  findBookingByReferenceNo: 成功查询
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(4)
    @DisplayName("TC-B-04 | findBookingByReferenceNo | 用有效 reference 查询，返回 BookingDTO")
    void findBookingByReferenceNo_success() {
        Assumptions.assumeTrue(createdBookingRef != null,
                "Skipped: TC-B-01 must pass first to provide createdBookingRef");
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", createdBookingRef)
        .then()
            .statusCode(200)
            .body("status",                   equalTo(200))
            .body("booking.bookingReference", equalTo(createdBookingRef));

        Integer id = given().spec(adminSpec).when()
                .get("/bookings/{ref}", createdBookingRef)
                .then().extract().path("booking.id");
        if (id != null) createdBookingId = id.longValue();
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-05  findBookingByReferenceNo: reference 不存在
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(5)
    @DisplayName("TC-B-05 | findBookingByReferenceNo | reference 不存在，抛出 NotFoundException")
    void findBookingByReferenceNo_notFound() {
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", "INVALID-REF-000")
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("not found"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-06  updateBooking: 状态转移 + 持久化验证
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(6)
    @DisplayName("TC-B-06 | updateBooking | 更新 bookingStatus + paymentStatus，验证持久化")
    void updateBooking_success_updateBothStatuses() {
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-04 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("bookingStatus", "CHECKED_IN");
        updateBody.put("paymentStatus", "COMPLETED");

        given()
            .spec(adminSpec)
            .body(updateBody)
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("updated"));

        // Verify both statuses persisted to DB
        given().spec(adminSpec).when()
               .get("/bookings/{ref}", createdBookingRef)
               .then()
               .body("booking.bookingStatus", equalTo("CHECKED_IN"))
               .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-07  updateBooking: id == null → NotFoundException
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(7)
    @DisplayName("TC-B-07 | updateBooking | id=null，抛出 NotFoundException('Booking id is required')")
    void updateBooking_fail_nullId() {
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("bookingStatus", "CANCELLED");

        given()
            .spec(adminSpec)
            .body(updateBody)
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("id"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-08  取消订单后验证房间重新可用（状态转移副作用）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(8)
    @DisplayName("TC-B-08 | cancelBooking | 取消订单后房间重新出现在可用列表中")
    void cancelBooking_roomBecomesAvailable() {
        String seedRoomType = given().spec(anonSpec)
                .when().get("/rooms/{id}", SEED_ROOM_ID)
                .then().extract().path("room.type");
        Assumptions.assumeTrue(seedRoomType != null, "Skipped: could not retrieve seed room type");

        // 1. Create booking for specific future dates
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(60), inDays(62)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        Integer bookingId = given().spec(adminSpec).when()
                .get("/bookings/{ref}", ref)
                .then().extract().path("booking.id");
        Assumptions.assumeTrue(bookingId != null, "Skipped: could not retrieve booking id");

        // 2. Admin cancels the booking
        Map<String, Object> cancelBody = new HashMap<>();
        cancelBody.put("id",            bookingId);
        cancelBody.put("bookingStatus", "CANCELLED");

        given()
            .spec(adminSpec)
            .body(cancelBody)
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200);

        // 3. Room should reappear in available list for those dates
        java.util.List<Integer> availableIds = given()
            .spec(anonSpec)
            .queryParam("checkInDate",  inDays(60))
            .queryParam("checkOutDate", inDays(62))
            .queryParam("roomType",     seedRoomType)
        .when()
            .get("/rooms/available")
        .then()
            .statusCode(200)
            .extract().jsonPath().getList("rooms.id");

        org.junit.jupiter.api.Assertions.assertTrue(
                availableIds != null && availableIds.contains(SEED_ROOM_ID.intValue()),
                "Room " + SEED_ROOM_ID + " should be available again after cancellation");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-09  createBooking: 重复预订同一房间相同日期 → 409
    //          验证对外行为契约：用户重复下单时系统拒绝
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(9)
    @DisplayName("TC-B-09 | createBooking | 重复预订相同日期 → 409")
    void createBooking_duplicate_returns409() {
        // TC-B-01 already booked tomorrow ~ inDays(2) for SEED_ROOM_ID
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, tomorrow(), inDays(2));

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(anyOf(is(400), is(409), is(422)))
            .body("message", containsStringIgnoringCase("not available"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-10  createBooking: 非法日期（checkOut < checkIn）→ 400
    //          Smoke test：验证 controller 层校验注解未被遗漏
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(10)
    @DisplayName("TC-B-10 | createBooking | checkOut < checkIn → 400（controller 层校验 smoke）")
    void createBooking_invalidDate_returns400() {
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, inDays(5), tomorrow());

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("message", containsStringIgnoringCase("before check in"));
    }
}
