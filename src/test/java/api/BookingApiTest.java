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
@DisplayName("Booking API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingApiTest extends BaseApiTest {

    /** A room that is guaranteed to exist in the test database. */
    private static final Long SEED_ROOM_ID = 1L;

    /** Shared across tests in this class. */
    private static String createdBookingRef;
    private static Long   createdBookingId;

    @Test @Order(1)
    @DisplayName("TC-B-01 | createBooking | success — returns booking with price and reference")
    void createBooking_success() {
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
            .body("booking", notNullValue())
            .body("booking.bookingReference", notNullValue())
            .body("booking.bookingReference", not(emptyString()))
            .extract().path("booking.bookingReference");
    }

    @Test @Order(2)
    @DisplayName("TC-B-02 | createBooking | room not found → 404")
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

    @Test @Order(3)
    @DisplayName("TC-B-03 | createBooking | checkIn in the past → 400/422")
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

    @Test @Order(4)
    @DisplayName("TC-B-04 | createBooking | checkIn == checkOut → 400/422")
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

    @Test @Order(5)
    @DisplayName("TC-B-05 | createBooking | [Bug] checkOut before checkIn — validation not triggered")
    void createBooking_bug_checkOutBeforeCheckIn() {
        // checkOut (tomorrow) is BEFORE checkIn (inDays(3))
        //
        // Root cause: BookingServiceImpl line 78 compares checkInDate with
        // itself (isBefore(checkInDate)) instead of
        // checkOutDate.isBefore(checkInDate).
        //
        // This test DOCUMENTS the bug:
        //   - Before fix: request succeeds (status 200)  ← BUG
        //   - After fix:  status 400/422 with error msg  ← EXPECTED
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

    @Test @Order(6)
    @DisplayName("TC-B-06 | createBooking | room not available for selected dates → 400/409/422")
    void createBooking_fail_roomNotAvailable() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
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

    @Test @Order(7)
    @DisplayName("TC-B-07 | createBooking | new booking has status BOOKED and payment PENDING")
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

        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", ref)
        .then()
            .statusCode(200)
            .body("booking.bookingStatus",  equalTo("BOOKED"))
            .body("booking.paymentStatus",  equalTo("PENDING"));
    }

    @Test @Order(8)
    @DisplayName("TC-B-08 | createBooking | missing roomId → 400/404, message contains 'roomId'")
    void createBooking_missingRoomId_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("checkInDate",  inDays(5));
        body.put("checkOutDate", inDays(7));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("roomId"));
    }

    @Test @Order(9)
    @DisplayName("TC-B-09 | createBooking | missing checkInDate → 400/422, message contains 'required'")
    void createBooking_missingCheckIn_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       SEED_ROOM_ID);
        body.put("checkOutDate", inDays(7));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("message", containsStringIgnoringCase("required"));
    }

    @Test @Order(10)
    @DisplayName("TC-B-10 | createBooking | missing checkOutDate → 400/422, message contains 'required'")
    void createBooking_missingCheckOut_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",      SEED_ROOM_ID);
        body.put("checkInDate", inDays(5));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("message", containsStringIgnoringCase("required"));
    }

    @Test @Order(11)
    @DisplayName("TC-B-11 | createBooking | invalid date format → 400, message not blank")
    void createBooking_invalidDateFormat_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       SEED_ROOM_ID);
        body.put("checkInDate",  "not-a-date");
        body.put("checkOutDate", "also-not-a-date");

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("message", not(emptyOrNullString()));
    }

    @Test @Order(12)
    @DisplayName("TC-B-12 | getAllBookings | each booking has user and room as null")
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

    @Test @Order(13)
    @DisplayName("TC-B-13 | findBookingByReferenceNo | valid reference → returns booking")
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

    @Test @Order(14)
    @DisplayName("TC-B-14 | findBookingByReferenceNo | unknown reference → 400/404")
    void findBookingByReferenceNo_notFound() {
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", "INVALID-REF-000")
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("not found"));
    }

    @Test @Order(15)
    @DisplayName("TC-B-15 | updateBooking | update both statuses → persisted")
    void updateBooking_success_updateBothStatuses() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-13 must pass first to provide createdBookingId");
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

        given().spec(adminSpec).when()
               .get("/bookings/{ref}", createdBookingRef)
               .then()
               .body("booking.bookingStatus", equalTo("CHECKED_IN"))
               .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    @Test @Order(16)
    @DisplayName("TC-B-16 | updateBooking | bookingStatus only — paymentStatus unchanged")
    void updateBooking_onlyBookingStatus_paymentStatusUnchanged() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-15 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("bookingStatus", "CANCELLED");
        // paymentStatus intentionally omitted → must remain PAID from TC-B-15

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

    @Test @Order(17)
    @DisplayName("TC-B-17 | updateBooking | paymentStatus only — bookingStatus unchanged")
    void updateBooking_onlyPaymentStatus_bookingStatusUnchanged() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-16 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("paymentStatus", "REFUNDED");
        // bookingStatus intentionally omitted → must remain CANCELLED from TC-B-16

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

    @Test @Order(18)
    @DisplayName("TC-B-18 | updateBooking | id=null → 400/404")
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

    @Test @Order(19)
    @DisplayName("TC-B-19 | updateBooking | id not found → 400/404")
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
