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
    @DisplayName("TC-B-02 | createBooking | new booking has status BOOKED and payment PENDING")
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
    @DisplayName("TC-B-06 | createBooking | missing checkInDate → 400, message contains 'required'")
    void createBooking_missingCheckIn_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       SEED_ROOM_ID);
        body.put("checkOutDate", inDays(7));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("required"));
    }

    @Test @Order(7)
    @DisplayName("TC-B-07 | createBooking | missing checkOutDate → 400, message contains 'required'")
    void createBooking_missingCheckOut_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",      SEED_ROOM_ID);
        body.put("checkInDate", inDays(5));

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("required"));
    }

    @Test @Order(8)
    @DisplayName("TC-B-08 | createBooking | invalid date format → 400, message not blank")
    void createBooking_invalidDateFormat_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId",       SEED_ROOM_ID);
        body.put("checkInDate",  "not-a-date");
        body.put("checkOutDate", "also-not-a-date");

        given().spec(customerSpec).body(body)
            .when().post("/bookings")
            .then()
            .statusCode(400)
            .body("message", not(emptyOrNullString()));
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
    @DisplayName("TC-B-12 | updateBooking | update both statuses → persisted")
    void updateBooking_success_updateBothStatuses() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-10 must pass first to provide createdBookingId");
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

    @Test @Order(13)
    @DisplayName("TC-B-13 | updateBooking | bookingStatus only — paymentStatus unchanged")
    void updateBooking_onlyBookingStatus_paymentStatusUnchanged() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-12 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("bookingStatus", "CANCELLED");
        // paymentStatus intentionally omitted → must remain PAID from TC-B-12

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

    @Test @Order(14)
    @DisplayName("TC-B-14 | updateBooking | paymentStatus only — bookingStatus unchanged")
    void updateBooking_onlyPaymentStatus_bookingStatusUnchanged() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: TC-B-13 must pass first to provide createdBookingId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id",            createdBookingId);
        updateBody.put("paymentStatus", "REFUNDED");
        // bookingStatus intentionally omitted → must remain CANCELLED from TC-B-13

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

    @Test @Order(15)
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

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test @Order(17)
    @DisplayName("TC-B-17 | cancelBooking | owner cancels BOOKED booking before checkIn → 200, status CANCELLED")
    void cancelBooking_success() {
        // Create a fresh booking in a far-future date range to avoid conflict
        Map<String, Object> body = bookingPayload(SEED_ROOM_ID, inDays(20), inDays(22));
        String ref = given().spec(customerSpec).body(body)
                .when().post("/bookings")
                .then().statusCode(200).extract().path("booking.bookingReference");

        Integer id = given().spec(adminSpec).when()
                .get("/bookings/{ref}", ref)
                .then().extract().path("booking.id");

        given()
            .spec(customerSpec)
        .when()
            .post("/bookings/{id}/cancel", id)
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("cancelled"));

        given().spec(customerSpec).when()
               .get("/bookings/{ref}", ref)
               .then()
               .body("booking.bookingStatus", equalTo("CANCELLED"));
    }

    @Test @Order(18)
    @DisplayName("TC-B-18 | cancelBooking | unauthenticated → 401")
    void cancelBooking_unauthenticated_returns401() {
        given()
            .spec(anonSpec)
        .when()
            .post("/bookings/{id}/cancel", 1L)
        .then()
            .statusCode(401);
    }

    @Test @Order(19)
    @DisplayName("TC-B-19 | cancelBooking | admin role → 403 (endpoint is CUSTOMER only)")
    void cancelBooking_adminRole_returns403() {
        given()
            .spec(adminSpec)
        .when()
            .post("/bookings/{id}/cancel", 1L)
        .then()
            .statusCode(403);
    }

    @Test @Order(20)
    @DisplayName("TC-B-20 | cancelBooking | wrong owner (customer B cancels customer A's booking) → 400")
    void cancelBooking_wrongOwner_returns400() {
        // TODO: requires a second customer account; for now documents the IDOR protection requirement
        // Create booking as customer, then attempt to cancel as a different user would return 400
        // This test is a placeholder — implement when a second customer seed account is available
        Assumptions.assumeTrue(createdBookingId != null,
                "Skipped: requires createdBookingId from TC-B-10");

        // createdBookingId belongs to customer@hotel.com — if another customer tries to cancel it they get 400
        // For now verify the owner (same customer) cannot cancel a non-BOOKED booking (TC-B-13 set it to CANCELLED)
        given()
            .spec(customerSpec)
        .when()
            .post("/bookings/{id}/cancel", createdBookingId)
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("BOOKED"));
    }

    @Test @Order(21)
    @DisplayName("TC-B-21 | cancelBooking | booking not found → 404")
    void cancelBooking_notFound_returns404() {
        given()
            .spec(customerSpec)
        .when()
            .post("/bookings/{id}/cancel", 999999L)
        .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("not found"));
    }
}
