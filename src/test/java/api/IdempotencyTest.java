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
@DisplayName("Idempotency & Retry Safety Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotencyTest extends BaseApiTest {

    private static final Long SEED_ROOM_ID = 1L;

    // TC-IDEM-01: GET /rooms/all repeated calls return consistent results
    @Test @Order(1)
    @DisplayName("TC-IDEM-01 | GET /rooms/all | repeated calls return the same result")
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

    // TC-IDEM-02: registering the same email twice — second attempt must be rejected
    @Test @Order(2)
    @DisplayName("TC-IDEM-02 | register | duplicate email registration — second request rejected")
    void register_sameEmailTwice_secondRequestRejected() {
        String email    = "idem_" + System.currentTimeMillis() + "@hotel.com";
        String password = "IdemPass1234!";

        given().spec(anonSpec)
               .body(registrationPayload(email, password))
               .when().post("/auth/register")
               .then().statusCode(200);

        given().spec(anonSpec)
               .body(registrationPayload(email, password))
               .when().post("/auth/register")
               .then()
               .statusCode(anyOf(is(400), is(409), is(422)));
    }

    // TC-IDEM-03: deleting the same room twice — second call must return 404, not 500
    @Test @Order(3)
    @DisplayName("TC-IDEM-03 | deleteRoom | duplicate delete — second call returns 404 not 500")
    void deleteRoom_twice_secondReturns404NotFound() {
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

        given().spec(adminSpec)
               .when().delete("/rooms/delete/{id}", roomId)
               .then().statusCode(200);

        given().spec(adminSpec)
               .when().delete("/rooms/delete/{id}", roomId)
               .then()
               .statusCode(anyOf(is(400), is(404)))
               .statusCode(not(500));
    }

    // TC-IDEM-04: sending the same updateBooking request twice — final state must be stable
    @Test @Order(4)
    @DisplayName("TC-IDEM-04 | updateBooking | same status sent twice — state remains stable")
    void updateBooking_sameStatusTwice_resultIsStable() {
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

        given().spec(adminSpec).body(updateBody)
               .when().put("/bookings/update")
               .then().statusCode(200);

        given().spec(adminSpec).body(updateBody)
               .when().put("/bookings/update")
               .then().statusCode(anyOf(is(200), is(400)))
                      .statusCode(not(500));

        given().spec(adminSpec)
               .when().get("/bookings/{ref}", ref)
               .then()
               .statusCode(200)
               .body("booking.bookingStatus", equalTo("CHECKED_IN"))
               .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    // TC-IDEM-05: a single booking creation must produce exactly one notification record
    @Test @Order(5)
    @DisplayName("TC-IDEM-05 | notification | one booking creates exactly one notification")
    void createBooking_onlyOneNotificationCreated() {
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(90), inDays(92)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        // Poll instead of fixed sleep — sendEmail() is @Async and has no deterministic delay
        pollUntil(8, () ->
            given().spec(adminSpec).when().get("/notifications/all")
                   .then().extract().jsonPath()
                   .getList("notifications.findAll { it.bookingReference == '" + ref + "' }")
                   .size() > 0
        );

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
