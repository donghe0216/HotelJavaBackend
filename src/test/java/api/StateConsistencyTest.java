package api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * State consistency tests.
 *
 * Verifies that business object state changes propagate correctly
 * across the system — i.e., that one operation's side effects are
 * visible and correct when queried through related endpoints.
 *
 * These tests do NOT test individual features in isolation.
 * They test the *relationships* between features.
 */
@DisplayName("State Consistency Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StateConsistencyTest extends BaseApiTest {

    private static final Long SEED_ROOM_ID = 1L;

    // TC-SC-01: after booking is created, the room must not appear in available rooms for the same dates
    @Test @Order(1)
    @DisplayName("TC-SC-01 | booking created | room not available for same dates")
    void afterBookingCreated_roomUnavailableForSameDates() {
        String checkIn  = inDays(40);
        String checkOut = inDays(42);

        given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, checkIn, checkOut))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200);

        given()
            .spec(anonSpec)
            .queryParam("checkInDate",  checkIn)
            .queryParam("checkOutDate", checkOut)
            .queryParam("roomType",     "SINGLE")
        .when()
            .get("/rooms/available")
        .then()
            .statusCode(200)
            // The booked room must not be in the available list
            .body("rooms.id.flatten()", not(hasItem(SEED_ROOM_ID.intValue())));
    }

    // TC-SC-02: after updateBooking, GET returns the new status immediately
    @Test @Order(2)
    @DisplayName("TC-SC-02 | updateBooking | GET reflects the new status after update")
    void afterBookingUpdated_queryReflectsNewStatus() {
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(50), inDays(52)))
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

        given()
            .spec(adminSpec)
            .body(java.util.Map.of("id", id, "bookingStatus", "CHECKED_IN", "paymentStatus", "COMPLETED"))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200);

        given()
            .spec(adminSpec)
        .when()
            .get("/bookings/{ref}", ref)
        .then()
            .statusCode(200)
            .body("booking.bookingStatus", equalTo("CHECKED_IN"))
            .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    // TC-SC-03: newly created booking appears in the user's booking history
    @Test @Order(3)
    @DisplayName("TC-SC-03 | booking created | appears in /users/bookings history")
    void afterBookingCreated_appearsInUserBookingHistory() {
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(60), inDays(62)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        given()
            .spec(customerSpec)
        .when()
            .get("/users/bookings")
        .then()
            .statusCode(200)
            .body("bookings.bookingReference.flatten()", hasItem(ref));
    }

    // TC-SC-04: deleted room must not appear in /rooms/all
    @Test @Order(4)
    @DisplayName("TC-SC-04 | room deleted | no longer appears in /rooms/all")
    void afterRoomDeleted_doesNotAppearInRoomList() {
        Integer newRoomId = given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    998)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
            .multiPart("description",   "Room for deletion test")
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(200)
            .extract().path("room.id");

        given()
            .spec(adminSpec)
        .when()
            .delete("/rooms/delete/{id}", newRoomId)
        .then()
            .statusCode(200);

        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/all")
        .then()
            .statusCode(200)
            .body("rooms.id.flatten()", not(hasItem(newRoomId)));
    }

    // TC-SC-05: deleting a room with active bookings — must not return 500
    @Test @Order(5)
    @DisplayName("TC-SC-05 | delete room with active booking | returns 200 (cascade) or 409 (conflict), never 500")
    void deleteRoomWithActiveBooking_behaviourIsConsistent() {
        Integer roomId = given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    997)
            .multiPart("type",          "DOUBLE")
            .multiPart("pricePerNight", "200.00")
            .multiPart("capacity",      4)
            .multiPart("description",   "Room with active booking")
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(200)
            .extract().path("room.id");

        given()
            .spec(customerSpec)
            .body(bookingPayload(roomId.longValue(), inDays(70), inDays(72)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200);

        int deleteStatus = given()
            .spec(adminSpec)
        .when()
            .delete("/rooms/delete/{id}", roomId)
        .then()
            .extract().statusCode();

        // The system should either:
        //   a) Reject the deletion (409 Conflict) to protect existing bookings
        //   b) Allow deletion and cascade (200) — both are valid business decisions
        // What is NOT acceptable: 500 Internal Server Error
        org.junit.jupiter.api.Assertions.assertNotEquals(500, deleteStatus,
            "Deleting a room with active bookings must not cause a 500 error. " +
            "Expected: 200 (cascade) or 409 (conflict). Got: " + deleteStatus);
    }
}
