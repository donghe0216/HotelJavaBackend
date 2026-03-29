package api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * API tests for PUT /payments/update.
 *
 * Covers three post-refactor guarantees:
 *   1. Ownership check  — only the booking owner can update payment status (403 otherwise)
 *   2. Idempotency      — duplicate COMPLETED calls are no-ops, not double-writes
 *   3. State guard      — CANCELLED bookings cannot be paid
 *
 * Pre-condition: at least one room must exist in the DB (resolved dynamically).
 */
@DisplayName("💳 Payment API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentApiTest extends BaseApiTest {

    private static Long   SEED_ROOM_ID;
    private static String bookingRef;   // shared across ordered tests

    @BeforeAll
    static void resolveRoom() {
        SEED_ROOM_ID = resolveFirstRoomId();
        assumeTrue(SEED_ROOM_ID != null,
                "Skipped: no rooms in DB — seed at least one room before running payment tests");
    }

    // helper
    private Map<String, Object> paymentPayload(String ref, boolean success) {
        Map<String, Object> m = new HashMap<>();
        m.put("bookingReference", ref);
        m.put("amount",           100.00);
        m.put("transactionId",    "txn_test_" + System.currentTimeMillis());
        m.put("success",          success);
        return m;
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-PAY-01  正常支付成功 → paymentStatus = COMPLETED
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-PAY-01 | updatePayment | success=true → paymentStatus=COMPLETED")
    void updatePayment_success_setsPaymentStatusCompleted() {
        // 1. Create a booking
        bookingRef = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(150), inDays(152)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        // 2. Simulate successful payment callback
        given()
            .spec(customerSpec)
            .body(paymentPayload(bookingRef, true))
        .when()
            .put("/payments/update")
        .then()
            .statusCode(200);

        // 3. Verify paymentStatus persisted as COMPLETED
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", bookingRef)
        .then()
            .statusCode(200)
            .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-PAY-02  重复调用同一成功事件 — 幂等性🔥
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(2)
    @DisplayName("TC-PAY-02 | updatePayment | duplicate call on COMPLETED booking is a no-op (idempotency)")
    void updatePayment_duplicateCall_isIdempotent() {
        assumeTrue(bookingRef != null, "Skipped: TC-PAY-01 must pass first");

        // Call the same payload again — should return 200 without re-writing
        given()
            .spec(customerSpec)
            .body(paymentPayload(bookingRef, true))
        .when()
            .put("/payments/update")
        .then()
            .statusCode(200);   // no-op, not 409 or 500

        // Status must still be COMPLETED (not duplicated or corrupted)
        given()
            .spec(customerSpec)
        .when()
            .get("/bookings/{ref}", bookingRef)
        .then()
            .statusCode(200)
            .body("booking.paymentStatus", equalTo("COMPLETED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-PAY-03  越权访问 — User B 修改 User A 的订单🔥
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(3)
    @DisplayName("TC-PAY-03 | updatePayment | another user updating someone else's booking → 403")
    void updatePayment_otherUser_returns403() {
        // 1. Create a fresh booking under customer account
        String refA = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(160), inDays(162)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        // 2. Register a second user (User B)
        String emailB    = "payment_b_" + System.currentTimeMillis() + "@hotel.com";
        String passwordB = "PayB1234!";
        given().spec(anonSpec)
               .body(registrationPayload(emailB, passwordB))
               .when().post("/auth/register")
               .then().statusCode(200);

        String tokenB = loginAndGetToken(emailB, passwordB);

        // 3. User B tries to update User A's booking — must be rejected
        given()
            .header("Authorization", "Bearer " + tokenB)
            .contentType("application/json")
            .accept("application/json")
            .body(paymentPayload(refA, true))
        .when()
            .put("/payments/update")
        .then()
            .statusCode(403);

        // Cleanup User B
        deleteAccount(tokenB);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-PAY-04  非法状态 — CANCELLED booking 不允许支付
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(4)
    @DisplayName("TC-PAY-04 | updatePayment | CANCELLED booking cannot be paid → 400")
    void updatePayment_cancelledBooking_returns400() {
        // 1. Create a booking
        String ref = given()
            .spec(customerSpec)
            .body(bookingPayload(SEED_ROOM_ID, inDays(170), inDays(172)))
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        // 2. Admin cancels the booking
        Integer id = given().spec(adminSpec)
            .when().get("/bookings/{ref}", ref)
            .then().extract().path("booking.id");

        given()
            .spec(adminSpec)
            .body(java.util.Map.of("id", id, "bookingStatus", "CANCELLED"))
        .when()
            .put("/bookings/update")
        .then()
            .statusCode(200);

        // 3. Attempt payment on CANCELLED booking — must be rejected
        given()
            .spec(customerSpec)
            .body(paymentPayload(ref, true))
        .when()
            .put("/payments/update")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("BOOKED"));
    }
}
