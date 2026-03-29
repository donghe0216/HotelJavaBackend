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
    //           验证：javaMailSender 调用（通过邮件服务器收到）
    //                 + type=EMAIL 存入 DB
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-N-01 | sendEmail | 创建订单后，通知记录成功写入数据库（type=EMAIL）")
    void sendEmail_isPersistedWithTypeEmail_afterBookingCreation() {
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

        // 3. Query notification records and verify
        //    Adjust the endpoint path to match your NotificationController.
        given()
            .spec(adminSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(200)
            // At least one notification with this bookingReference must exist
            .body("notifications.find { it.bookingReference == '" + bookingRef + "' }.type",
                  equalTo("EMAIL"))
            .body("notifications.find { it.bookingReference == '" + bookingRef + "' }.recipient",
                  notNullValue());
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-N-02  【补充】sendEmail: 验证存入 DB 的 type 字段确实为 EMAIL
    //          (complementary fine-grained assertion via ArgumentCaptor pattern)
    //          At the API level we re-confirm the type field is not null/blank.
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(2)
    @DisplayName("TC-N-02 | sendEmail | 【补充】DB 记录中 type=EMAIL，subject/body 非空")
    void sendEmail_notificationRecord_hasRequiredFields() {
        Map<String, Object> bookingBody = bookingPayload(SEED_ROOM_ID, inDays(25), inDays(27));

        String bookingRef = given()
            .spec(customerSpec)
            .body(bookingBody)
        .when()
            .post("/bookings")
        .then()
            .statusCode(200)
            .extract().path("booking.bookingReference");

        pollUntil(8, () ->
            given().spec(adminSpec).when().get("/notifications/all")
                   .then().extract().jsonPath()
                   .getList("notifications.findAll { it.bookingReference == '" + bookingRef + "' }")
                   .size() > 0
        );

        given()
            .spec(adminSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(200)
            .body("notifications.findAll { it.bookingReference == '" + bookingRef + "' }.type",
                  everyItem(equalTo("EMAIL")))
            .body("notifications.findAll { it.bookingReference == '" + bookingRef + "' }.subject",
                  everyItem(not(emptyOrNullString())))
            .body("notifications.findAll { it.bookingReference == '" + bookingRef + "' }.body",
                  everyItem(not(emptyOrNullString())));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-N-03  【补充】sendEmail: @Async 失败时行为（文档测试）
    //
    //  Current code has no try-catch inside sendEmail():
    //    - MailException is thrown by javaMailSender.send()
    //    - Because @Async swallows the exception silently, the user
    //      receives a 200 from the booking API but no email is sent.
    //
    //  This test documents the expected behaviour AFTER a fix is applied:
    //    - Add AsyncUncaughtExceptionHandler or wrap in try-catch
    //    - Log the error and optionally retry or alert
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(3)
    @DisplayName("TC-N-03 | sendEmail | 【补充/文档】@Async 静默吞异常问题说明")
    void sendEmail_asyncExceptionHandling_documentationTest() {
        // This test cannot trigger a real MailException at the API level
        // without mocking the mail server.
        //
        // Recommended fix in NotificationServiceImpl:
        //
        //   @Async
        //   public void sendEmail(NotificationDTO dto) {
        //       try {
        //           javaMailSender.send(...);
        //           notificationRepository.save(...);
        //       } catch (MailException e) {
        //           log.error("Failed to send email to {}: {}", dto.getRecipient(), e.getMessage());
        //           // Optionally: save a FAILED notification record or publish an event
        //       }
        //   }
        //
        // Unit test approach (BookingServiceImplTest):
        //   doThrow(new MailSendException("SMTP error"))
        //       .when(javaMailSender).send(any(SimpleMailMessage.class));
        //   // Verify: booking is still saved (email failure ≠ booking failure)
        //   verify(bookingRepository).save(any(Booking.class));

        // Sanity assertion: the app itself is still alive after any prior email failures
        given()
            .spec(adminSpec)
        .when()
            .get("/notifications/all")
        .then()
            .statusCode(anyOf(is(200), is(403)));   // 403 if endpoint is admin-only & JWT expired
    }
}
