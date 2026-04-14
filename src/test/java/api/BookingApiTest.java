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
 *
 * Routes covered:
 *   POST   /bookings                       (CUSTOMER / authenticated)
 *   GET    /bookings/all                   (ADMIN)
 *   GET    /bookings/{ref}
 *   PUT    /bookings/update                (ADMIN)
 *   POST   /bookings/{id}/cancel           (CUSTOMER / ADMIN)
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
            .body("status",                   equalTo(200))
            .body("message",                  containsStringIgnoringCase("success"))
            .body("booking",                  notNullValue())
            .body("booking.bookingReference", notNullValue())
            .body("booking.bookingReference", not(emptyString()))
            .body("booking.bookingStatus",    equalTo("BOOKED"))
            .body("booking.totalPrice",       greaterThan(0.0f))
            .extract().path("booking.bookingReference");
    }

    @Test @Order(2)
    @DisplayName("TC-B-02 | createBooking | new booking has status BOOKED")
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
            .body("booking.bookingStatus", equalTo("BOOKED"));
    }

    @Test @Order(3)
    @DisplayName("TC-B-03 | createBooking | room not found → 404")
    void createBooking_fail_roomNotFound() {
        Map<String, Object> body = bookingPayload(999999L, tomorrow(), inDays(3));

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("room"));
    }

    @Test @Order(4)
    @DisplayName("TC-B-04 | createBooking | room not available for selected dates → 400")
    void createBooking_fail_roomNotAvailable() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, tomorrow(), inDays(3));

        given()
            .spec(customerSpec)
            .body(body)
        .when()
            .post("/bookings")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("not available"));
    }

    @Test @Order(5)
    @DisplayName("TC-B-05 | createBooking | missing roomId → 404, message contains 'roomId'")
    void createBooking_missingRoomId_returns404() {
        Map<String, Object> body = new HashMap<>();
        body.put("checkInDate",  inDays(5));
        body.put("checkOutDate", inDays(7));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("roomId"));
    }

    @Test @Order(6)
    @DisplayName("TC-B-06 | createBooking | missing checkInDate → 400, message contains 'checkIn'")
    void createBooking_missingCheckIn_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       SEED_ROOM_ID);
        body.put("checkOutDate", inDays(7));

        // Backend checks both dates in one branch: "checkInDate and checkOutDate are required".
        // The message contains both field names, so TC-B-06 and TC-B-07 hit the same code path.
        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("checkIn"));
    }

    @Test @Order(7)
    @DisplayName("TC-B-07 | createBooking | missing checkOutDate → 400, message contains 'checkOut'")
    void createBooking_missingCheckOut_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",      SEED_ROOM_ID);
        body.put("checkInDate", inDays(5));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("checkOut"));
    }

    @Test @Order(8)
    @DisplayName("TC-B-08 | createBooking | invalid date format → 400")
    void createBooking_invalidDateFormat_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       SEED_ROOM_ID);
        body.put("checkInDate",  "not-a-date");
        body.put("checkOutDate", "also-not-a-date");

        // Invalid JSON type triggers HttpMessageNotReadableException (Jackson deserialization failure).
        // GlobalExceptionHandler does not override handleHttpMessageNotReadable, so Spring's default
        // handler runs — response body may be empty. Only assert the status code.
        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(400);
    }

    @Test @Order(9)
    @DisplayName("TC-B-09 | getAllBookings | each booking has user and room as null")
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

    @Test @Order(10)
    @DisplayName("TC-B-10 | findBookingByReferenceNo | valid reference → returns booking")
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

    @Test @Order(11)
    @DisplayName("TC-B-11 | findBookingByReferenceNo | unknown reference → 404")
    void findBookingByReferenceNo_notFound() {
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", "INVALID-REF-000")
        .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("not found"));
    }

    @Test @Order(12)
    @DisplayName("TC-B-12 | updateBooking | BOOKED → CHECKED_IN → persisted")
    void updateBooking_success_checkedIn() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-10 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("bookingStatus", "CHECKED_IN");

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
               .body("booking.bookingStatus", equalTo("CHECKED_IN"));
    }

    @Test @Order(13)
    @DisplayName("TC-B-15 | updateBooking | id=null → 404")
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
            .statusCode(404)
            .body("message", containsStringIgnoringCase("id"));
    }

    @Test @Order(16)
    @DisplayName("TC-B-16 | updateBooking | id not found → 404")
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
            .statusCode(404)
            .body("message", containsStringIgnoringCase("not found"));
    }

    @Test @Order(17)
    @DisplayName("TC-B-17 | cancelBooking | BOOKED booking cancelled by owner → 200, status persisted as CANCELLED")
    void cancelBooking_success() {
        Long roomId = resolveFirstRoomId();
        org.junit.jupiter.api.Assumptions.assumeTrue(roomId != null, "Skipped: no room in DB");

        // Create a fresh booking with check-in far enough away to satisfy the 24-hour policy
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(roomId, inDays(5), inDays(7)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        Integer id = given().spec(adminSpec).when()
            .get("/bookings/{ref}", ref)
            .then().extract().path("booking.id");

        given()
            .spec(customerSpec)
        .when()
            .post("/bookings/{id}/cancel", id)
        .then()
            .statusCode(200)
            .body("message", containsStringIgnoringCase("cancelled"));

        // Verify status is actually persisted — not just returned in the response
        given().spec(adminSpec).when()
            .get("/bookings/{ref}", ref)
        .then()
            .body("booking.bookingStatus", equalTo("CANCELLED"));
    }

    @Test @Order(18)
    @DisplayName("TC-B-18 | cancelBooking | booking not found → 404")
    void cancelBooking_notFound() {
        given()
            .spec(customerSpec)
        .when()
            .post("/bookings/{id}/cancel", 999999L)
        .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("not found"));
    }
}
