package api;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * API contract tests for the BookingStatus state machine.
 *
 * These tests verify that the HTTP layer correctly returns 200 for valid
 * transitions and 400 for invalid ones — not the transition logic itself.
 * The transition logic is exhaustively covered in BookingServiceImplTest
 * (TC-BS-17 / TC-BS-18) using parameterised unit tests.
 *
 * State machine (offline-payment hotel):
 *   BOOKED     → CHECKED_IN  ✅
 *   BOOKED     → CANCELLED   ✅
 *   BOOKED     → NO_SHOW     ✅
 *   CHECKED_IN → CHECKED_OUT ✅
 *   All other transitions     ❌ → 400
 */
@DisplayName("Booking State Machine API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingStateMachineApiTest extends BaseApiTest {

    private static Long SEED_ROOM_ID;

    @BeforeAll
    static void resolveRoom() {
        SEED_ROOM_ID = resolveFirstRoomId();
        Assumptions.assumeTrue(SEED_ROOM_ID != null,
                "Skipped: no rooms in DB — seed at least one room before running state machine tests");
    }

    @Test @Order(1)
    @DisplayName("TC-BSM-01 | BOOKED → CHECKED_IN | valid transition — 200 + status persisted in DB")
    void booked_to_checkedIn_isValid() {
        String ref = createFreshBooking(30);
        Long id    = resolveBookingId(ref);

        given()
            .spec(adminSpec)
            .body(Map.of("id", id, "bookingStatus", "CHECKED_IN"))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200)
            .body("status", equalTo(200));

        // Verify the new status is actually persisted — not just returned in the update response
        given().spec(adminSpec).when()
            .get("/bookings/{ref}", ref)
        .then()
            .body("booking.bookingStatus", equalTo("CHECKED_IN"));
    }

    @Test @Order(2)
    @DisplayName("TC-BSM-02 | CHECKED_IN → CHECKED_OUT | valid transition — 200 + status persisted in DB")
    void checkedIn_to_checkedOut_isValid() {
        String ref = createFreshBooking(35);
        Long id    = resolveBookingId(ref);
        forceUpdate(id, "CHECKED_IN");

        given()
            .spec(adminSpec)
            .body(Map.of("id", id, "bookingStatus", "CHECKED_OUT"))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200);

        given().spec(adminSpec).when()
            .get("/bookings/{ref}", ref)
        .then()
            .body("booking.bookingStatus", equalTo("CHECKED_OUT"));
    }

    @Test @Order(3)
    @DisplayName("TC-BSM-07 | CHECKED_OUT → CANCELLED | terminal state violation — 400 with message")
    void checkedOut_to_cancelled_isRejected() {
        String ref = createFreshBooking(40);
        Long id    = resolveBookingId(ref);
        forceUpdate(id, "CHECKED_IN");
        forceUpdate(id, "CHECKED_OUT");  // advance to terminal state

        given()
            .spec(adminSpec)
            .body(Map.of("id", id, "bookingStatus", "CANCELLED"))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("Invalid status transition"));
    }

    @Test @Order(4)
    @DisplayName("TC-BSM-08 | cancelBooking endpoint | CHECKED_IN → cancel → 400")
    void checkedIn_cancel_isRejected() {
        String ref = createFreshBooking(50);
        Long id    = resolveBookingId(ref);
        forceUpdate(id, "CHECKED_IN");

        given()
            .spec(adminSpec)
        .when()
            .post("/bookings/{id}/cancel", id)
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("Cannot cancel"));
    }

    @Test @Order(5)
    @DisplayName("TC-BSM-09 | cancelBooking endpoint | already CANCELLED → cancel again → 400")
    void alreadyCancelled_cancelAgain_isRejected() {
        String ref = createFreshBooking(55);
        Long id    = resolveBookingId(ref);

        // First cancel — should succeed
        given().spec(customerSpec).when()
            .post("/bookings/{id}/cancel", id)
            .then().statusCode(200);

        // Second cancel — booking is already CANCELLED, must be rejected
        given()
            .spec(customerSpec)
        .when()
            .post("/bookings/{id}/cancel", id)
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("Cannot cancel"));
    }

    /** Creates a booking in BOOKED state; dateOffset separates date windows across tests to avoid availability conflicts. */
    private String createFreshBooking(int dateOffset) {
        return given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(dateOffset), inDays(dateOffset + 2)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");
    }

    private Long resolveBookingId(String ref) {
        Integer id = given().spec(adminSpec).when()
            .get("/bookings/{ref}", ref)
            .then().extract().path("booking.id");
        return id.longValue();
    }

    /**
     * Advances booking to the given status without asserting the response.
     * Used only for test setup — not part of the scenario under test.
     */
    private void forceUpdate(Long id, String status) {
        given()
            .spec(adminSpec)
            .body(Map.of("id", id, "bookingStatus", status))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200);
    }
}
