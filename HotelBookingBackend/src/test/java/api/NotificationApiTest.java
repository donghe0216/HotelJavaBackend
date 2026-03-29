package api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * API tests for Notification.
 *
 * NotificationService is an internal service (no direct REST endpoint).
 * We test its behaviour indirectly: by creating a Booking we trigger
 * sendEmail(), then verify the stored notification record via an admin endpoint.
 *
 * Assumed route:
 *   GET  /notifications/all          (ADMIN)  ← adjust if different
 *
 * The test also validates:
 *   - The notification is persisted with type=EMAIL
 *   - The bookingReference stored in the notification matches the booking
 */
@DisplayName("📧 Notification API Tests (Indirect via Booking)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationApiTest extends BaseApiTest {

    // Resolved dynamically — not hardcoded to 1L (auto_increment gaps cause failures)
    private static Long SEED_ROOM_ID;

    @BeforeAll
    static void resolveRoom() {
        SEED_ROOM_ID = resolveFirstRoomId();
        assumeTrue(SEED_ROOM_ID != null,
                "Skipped: no rooms in DB — seed at least one room before running notification tests");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-N-01  sendEmail (indirect): 创建预订后，通知记录写入数据库
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-N-01 | sendEmail | 创建订单后通知记录写入DB：type=EMAIL，recipient/subject/body 非空")
    void sendEmail_isPersistedWithCorrectFields_afterBookingCreation() {
        // 1. Create a booking → triggers sendEmail() internally
        Map<String, Object> bookingBody = bookingPayload(SEED_ROOM_ID, inDays(20), inDays(22));

        String bookingRef = given()
            .spec(customerSpec)
            .body(bookingBody)
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        // 2. Wait for @Async sendEmail to complete (poll instead of fixed sleep)
        pollUntil(8, () ->
            given().spec(adminSpec).when().get("/notifications/all")
                   .then().extract().jsonPath()
                   .getList("notifications.findAll { it.bookingReference == '" + bookingRef + "' }")
                   .size() > 0
        );

        // 3. Verify all required fields in the persisted notification record
        given()
            .spec(adminSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(200)
            .body("notifications.find { it.bookingReference == '" + bookingRef + "' }.type",
                  equalTo("EMAIL"))
            .body("notifications.find { it.bookingReference == '" + bookingRef + "' }.recipient",
                  notNullValue())
            .body("notifications.find { it.bookingReference == '" + bookingRef + "' }.subject",
                  not(emptyOrNullString()))
            .body("notifications.find { it.bookingReference == '" + bookingRef + "' }.body",
                  not(emptyOrNullString()));
    }
}
