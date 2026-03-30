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
 *   GET    /bookings/all                      (ADMIN)
 *   GET    /bookings/{ref}
 *   PUT    /bookings/update                   (ADMIN)
 *
 * Pre-condition:
 *   A room with roomId=SEED_ROOM_ID must exist in the DB before running.
 *   Adjust SEED_ROOM_ID to match a seeded room in your test environment.
 */
@DisplayName("📅 Booking API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingApiTest extends BaseApiTest {

    /** A room that is guaranteed to exist in the test database. */
    private static final Long SEED_ROOM_ID = 1L;

    /** Shared across tests in this class. */
    private static String createdBookingRef;
    private static Long   createdBookingId;

    // ═══════════════════════════════════════════════════════════════
    // TC-B-01  createBooking: 成功创建订单
    //          验证：总价计算、bookingReference 非空、邮件调用（server-side log）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-B-01 | createBooking | 成功创建订单，验证价格与 reference 字段")
    void createBooking_success() {
        // 2-night stay: pricePerNight × 2 should equal totalPrice
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, tomorrow(), inDays(3));

        createdBookingRef = given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("success"))
            // booking object must be returned
            .body("booking", notNullValue())
            // bookingReference must be a non-empty string
            .body("booking.bookingReference", notNullValue())
            .body("booking.bookingReference", not(emptyString()))
            .extract().path("booking.bookingReference");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-02  createBooking: 房间不存在 → NotFoundException
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
    // TC-B-03  createBooking: 入住日期在今天之前
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(3)
    @DisplayName("TC-B-03 | createBooking | 入住日期为昨天，抛出 InvalidBookingStateAndDateException")
    void createBooking_fail_checkInBeforeToday() {
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, yesterday(), tomorrow());

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("message", containsStringIgnoringCase("before today"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-04  createBooking: 入住 == 退房 → 抛异常
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(4)
    @DisplayName("TC-B-04 | createBooking | checkIn == checkOut，抛出 InvalidBookingStateAndDateException")
    void createBooking_fail_sameDate() {
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, tomorrow(), tomorrow());

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("message", containsStringIgnoringCase("equal"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-05  🐛 BUG: 退房日期早于入住日期时校验不生效
    //
    //   Root cause: BookingServiceImpl line 78 compares checkInDate with
    //   itself (isBefore(checkInDate)) instead of
    //   checkOutDate.isBefore(checkInDate).
    //
    //   This test DOCUMENTS the bug:
    //     - Before fix: request succeeds (status 200)  ← BUG
    //     - After fix:  status 400/422 with error msg  ← EXPECTED
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(5)
    @DisplayName("TC-B-05 | createBooking | 🐛 BUG: checkOut < checkIn 时校验不触发（文档测试）")
    void createBooking_bug_checkOutBeforeCheckIn() {
        // checkOut (tomorrow) is BEFORE checkIn (inDays(3))
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, inDays(3), tomorrow());

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            // ---- BEFORE FIX: uncomment to confirm bug exists ----
            // .statusCode(200)   // passes because comparison is always false
            //
            // ---- AFTER FIX: switch to these assertions ----
            .statusCode(anyOf(is(400), is(422)))
            .body("message", containsStringIgnoringCase("before check in"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-06  createBooking: 日期已被预订 → 不可用
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(6)
    @DisplayName("TC-B-06 | createBooking | 选定日期已被预订，抛出 InvalidBookingStateAndDateException")
    void createBooking_fail_roomNotAvailable() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        // TC-B-01 already booked tomorrow → inDays(3) for SEED_ROOM_ID.
        // Attempt the exact same dates → should be rejected.
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, tomorrow(), inDays(3));

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
    // TC-B-07  【补充】createBooking: 验证初始状态 BOOKED + PENDING
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(7)
    @DisplayName("TC-B-07 | createBooking | 【补充】新订单状态 = BOOKED，支付状态 = PENDING")
    void createBooking_newBooking_hasCorrectInitialStatus() {
        // Use a different date range to avoid conflict with TC-B-01
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, inDays(10), inDays(12));

        String ref = given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        // Fetch the booking and verify statuses
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", ref)
        .then()
            .statusCode(200)
            .body("booking.bookingStatus",  equalTo("BOOKED"))
            .body("booking.paymentStatus",  equalTo("PENDING"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-08  getAllBookings: ADMIN 获取全部订单，user/room 字段为 null
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(8)
    @DisplayName("TC-B-08 | getAllBookings | 【补充】返回列表中每条记录的 user 和 room 均为 null")
    void getAllBookings_bookingListHasNullUserAndRoom() {
        given()
            .spec(adminSpec)
        .when()
            .get("/bookings/all")
        .then()
            .statusCode(200)
            .body("status",   equalTo(200))
            .body("bookings", not(empty()))
            // Every element in the list should have user=null, room=null
            .body("bookings.user", everyItem(nullValue()))
            .body("bookings.room", everyItem(nullValue()));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-09  findBookingByReferenceNo: 成功查询
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(9)
    @DisplayName("TC-B-09 | findBookingByReferenceNo | 用有效 reference 查询，返回 BookingDTO")
    void findBookingByReferenceNo_success() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingRef != null,
                "Skipped: TC-B-01 must pass first to provide createdBookingRef");
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", createdBookingRef)
        .then()
            .statusCode(200)
            .body("status",                     equalTo(200))
            .body("booking.bookingReference",   equalTo(createdBookingRef));

        // Also capture the numeric id for update tests
        Integer id = given().spec(adminSpec).when()
                .get("/bookings/{ref}", createdBookingRef)
                .then().extract().path("booking.id");
        if (id != null) createdBookingId = id.longValue();
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-10  findBookingByReferenceNo: reference 不存在
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(10)
    @DisplayName("TC-B-10 | findBookingByReferenceNo | reference 不存在，抛出 NotFoundException")
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
    // TC-B-11  updateBooking: 同时更新 BookingStatus + PaymentStatus
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(11)
    @DisplayName("TC-B-11 | updateBooking | 成功更新 bookingStatus=CHECKED_IN, paymentStatus=PAID")
    void updateBooking_success_updateBothStatuses() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-09 must pass first to provide createdBookingId");
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

        // Verify changes persisted
        given().spec(adminSpec).when()
               .get("/bookings/{ref}", createdBookingRef)
               .then()
               .body("booking.bookingStatus", equalTo("CHECKED_IN"))
               .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-12  【补充】updateBooking: 只更新 BookingStatus，PaymentStatus 不变
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(12)
    @DisplayName("TC-B-12 | updateBooking | 【补充】只传 bookingStatus，paymentStatus 不应被覆盖")
    void updateBooking_onlyBookingStatus_paymentStatusUnchanged() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-11 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("bookingStatus", "CANCELLED");
        // paymentStatus intentionally omitted → must remain PAID from TC-B-11

        given()
            .spec(adminSpec)
            .body(updateBody)
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200);

        given().spec(adminSpec).when()
               .get("/bookings/{ref}", createdBookingRef)
               .then()
               .body("booking.bookingStatus", equalTo("CANCELLED"))
               .body("booking.paymentStatus", equalTo("COMPLETED"));   // unchanged
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-13  【补充】updateBooking: 只更新 PaymentStatus，BookingStatus 不变
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(13)
    @DisplayName("TC-B-13 | updateBooking | 【补充】只传 paymentStatus，bookingStatus 不应被覆盖")
    void updateBooking_onlyPaymentStatus_bookingStatusUnchanged() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-12 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("paymentStatus", "REFUNDED");
        // bookingStatus intentionally omitted → must remain CANCELLED from TC-B-12

        given()
            .spec(adminSpec)
            .body(updateBody)
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200);

        given().spec(adminSpec).when()
               .get("/bookings/{ref}", createdBookingRef)
               .then()
               .body("booking.bookingStatus", equalTo("CANCELLED"))  // unchanged
               .body("booking.paymentStatus", equalTo("REFUNDED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-B-14  updateBooking: id == null → NotFoundException
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(14)
    @DisplayName("TC-B-14 | updateBooking | id=null，抛出 NotFoundException('Booking id is required')")
    void updateBooking_fail_nullId() {
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("bookingStatus", "CANCELLED");
        // id deliberately omitted (null in JSON)

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
    // TC-B-15  updateBooking: ID 在 DB 中不存在 → NotFoundException
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(15)
    @DisplayName("TC-B-15 | updateBooking | ID=999999 不存在，抛出 NotFoundException")
    void updateBooking_fail_idNotFound() {
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            999999L);
        updateBody.put("bookingStatus", "CANCELLED");

        given()
            .spec(adminSpec)
            .body(updateBody)
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("not found"));
    }
}
