package api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for the User/Auth API.
 *
 * Verifies that the system correctly handles race conditions
 * during user registration — specifically that database-level
 * unique constraints prevent duplicate accounts from being created.
 *
 * Isolation: the successfully registered account is deleted in @AfterAll.
 */
@DisplayName("⚡ Concurrent User Tests")
class ConcurrentUserTest extends BaseApiTest {

    private static final int THREAD_COUNT    = 5;
    private static final int TIMEOUT_SECONDS = 15;

    // Track the shared email so we can clean up after the test
    private static String registeredEmail;
    private static String registeredPassword;

    @AfterAll
    static void cleanUp() {
        if (registeredEmail == null) return;
        try {
            String token = loginAndGetToken(registeredEmail, registeredPassword);
            deleteAccount(token);
        } catch (Exception ignored) { /* account may not exist if all registrations failed */ }
        registeredEmail    = null;
        registeredPassword = null;
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-CON-U-01  同一邮箱并发注册，只能成功创建1个账号
    //
    // 如果后端没有数据库唯一约束，多个请求可能同时通过校验并
    // 各自 INSERT，导致同一邮箱存在多条记录 —— 这是安全漏洞。
    // ═══════════════════════════════════════════════════════════════
    @Test
    @DisplayName("TC-CON-U-01 | register | 同一邮箱并发注册，只有1个账号被创建")
    void concurrentRegister_sameEmail_onlyOneAccountCreated() throws InterruptedException {
        registeredEmail    = "concurrent_" + System.currentTimeMillis() + "@hotel.com";
        registeredPassword = "ConPass1234!";

        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch  startGate = new CountDownLatch(1);
        CountDownLatch  doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        List<Integer> statusCodes  = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    int status = given()
                            .spec(anonSpec)
                            .body(registrationPayload(registeredEmail, registeredPassword))
                            .when()
                            .post("/auth/register")
                            .then()
                            .extract().statusCode();
                    statusCodes.add(status);
                    if (status == 200) successCount.incrementAndGet();
                    else               failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = doneLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Timed out waiting for concurrent register requests");
        assertEquals(THREAD_COUNT, statusCodes.size(),
                "Expected " + THREAD_COUNT + " responses but got " + statusCodes.size());
        assertEquals(1, successCount.get(),
                "Duplicate email registration: expected exactly 1 success, but got: "
                + successCount.get() + ". Status codes: " + statusCodes
                + "\n→ POSSIBLE BUG: missing DB unique constraint on email column");
        assertEquals(THREAD_COUNT - 1, failCount.get(),
                "Expected " + (THREAD_COUNT - 1) + " rejected requests, but got: " + failCount.get());
    }
}
