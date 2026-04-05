package api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Concurrency tests for the Booking API.
 *
 * These tests verify that the system correctly handles race conditions
 * when multiple users attempt to book the same room at the same time.
 *
 * Pre-condition: At least one room must exist in the database.
 * SEED_ROOM_ID is resolved dynamically at runtime by fetching the first
 * available room from GET /rooms/all — no hardcoded ID dependency.
 *
 * Isolation: all test users created here are deleted in @AfterAll.
 * Date offsets are seeded per-run to avoid booking conflicts across runs.
 */
@DisplayName("Concurrent Booking Tests")
class ConcurrentBookingTest extends BaseApiTest {

    // Resolved in @BeforeAll after RestAssured is configured — not a static initializer
    // (static initializers run before @BeforeAll, before RestAssured.baseURI is set)
    private static Long SEED_ROOM_ID;
    private static final int THREAD_COUNT    = 10;
    private static final int TIMEOUT_SECONDS = 15;

    // Per-run date base: spreads dates across runs to avoid conflicts
    // Uses last 3 digits of nanoTime to pick a base between 200-299
    private static final int DATE_BASE = 200 + (int)(System.nanoTime() % 100);

    // Track all tokens created in this test class for cleanup
    private static final List<String> createdTokens = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void resolveSeedRoom() {
        // Runs after BaseApiTest.globalSetup() — RestAssured is fully configured here
        SEED_ROOM_ID = resolveFirstRoomId();
        assumeTrue(SEED_ROOM_ID != null,
                "Skipped: no rooms found in DB — seed at least one room before running concurrent booking tests");
    }

    @AfterAll
    static void cleanUp() {
        createdTokens.forEach(BaseApiTest::deleteAccount);
        createdTokens.clear();
    }

    // TC-CON-01: 10 concurrent users booking the same room — only one succeeds
    @Test
    @DisplayName("TC-CON-01 | createBooking | 10 concurrent requests for same room — only 1 succeeds")
    void concurrentBooking_sameRoomSameDates_onlyOneSucceeds() throws InterruptedException {
        String checkIn  = inDays(DATE_BASE);
        String checkOut = inDays(DATE_BASE + 2);

        ExecutorService executor  = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch  startGate = new CountDownLatch(1);
        CountDownLatch  doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        List<Integer> statusCodes  = new CopyOnWriteArrayList<>();
        List<Integer> serverErrors = new CopyOnWriteArrayList<>(); // 5xx responses tracked separately — race condition indicator

        // Register THREAD_COUNT unique users and pre-fetch their tokens
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            String email    = "con01_" + i + "_" + System.currentTimeMillis() + "@hotel.com";
            String password = "ConPass1234!";
            given().spec(anonSpec)
                   .body(registrationPayload(email, password))
                   .when().post("/auth/register")
                   .then().statusCode(200);
            String token = loginAndGetToken(email, password);
            tokens.add(token);
            createdTokens.add(token);
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            final String token = tokens.get(i);
            executor.submit(() -> {
                try {
                    startGate.await();
                    Map<String, Object> body = bookingPayload(SEED_ROOM_ID, checkIn, checkOut);
                    int status = given()
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .accept("application/json")
                            .body(body)
                            .when()
                            .post("/bookings")
                            .then()
                            .extract().statusCode();
                    statusCodes.add(status);
                    if (status == 200)       successCount.incrementAndGet();
                    else if (status >= 500)  serverErrors.add(status);  // 5xx = race condition bug
                    else                     failCount.incrementAndGet();
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

        assertTrue(completed, "Timed out waiting for concurrent requests to complete");
        assertEquals(THREAD_COUNT, statusCodes.size(),
                "Expected " + THREAD_COUNT + " responses but got " + statusCodes.size());
        assertTrue(serverErrors.isEmpty(),
                "5xx errors detected — possible race condition bug in booking logic: " + serverErrors);
        assertEquals(1, successCount.get(),
                "Expected exactly 1 successful booking, but got: " + successCount.get()
                + ". Status codes: " + statusCodes);
        assertEquals(THREAD_COUNT - 1, failCount.get(),
                "Expected " + (THREAD_COUNT - 1) + " failed bookings, but got: " + failCount.get());
    }

    // TC-CON-02: same user submits duplicate booking (double-click / network retry scenario)
    @Test
    @DisplayName("TC-CON-02 | createBooking | same user submits duplicate request — only 1 booking created")
    void concurrentBooking_sameUserDuplicateSubmit_onlyOneCreated() throws InterruptedException {
        String checkIn  = inDays(DATE_BASE + 10);
        String checkOut = inDays(DATE_BASE + 12);

        String email    = "con02_" + System.currentTimeMillis() + "@hotel.com";
        String password = "DupPass1234!";
        given().spec(anonSpec)
               .body(registrationPayload(email, password))
               .when().post("/auth/register")
               .then().statusCode(200);
        String token = loginAndGetToken(email, password);
        createdTokens.add(token);

        int dupCount = 5;
        ExecutorService executor  = Executors.newFixedThreadPool(dupCount);
        CountDownLatch  startGate = new CountDownLatch(1);
        CountDownLatch  doneLatch = new CountDownLatch(dupCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Integer> statusCodes  = new CopyOnWriteArrayList<>();

        for (int i = 0; i < dupCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    Map<String, Object> body = bookingPayload(SEED_ROOM_ID, checkIn, checkOut);
                    int status = given()
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .accept("application/json")
                            .body(body)
                            .when()
                            .post("/bookings")
                            .then()
                            .extract().statusCode();
                    statusCodes.add(status);
                    if (status == 200) successCount.incrementAndGet();
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

        assertTrue(completed, "Timed out waiting for duplicate-submit requests");
        assertEquals(1, successCount.get(),
                "Duplicate submit: expected exactly 1 booking created, but got: "
                + successCount.get() + ". Status codes: " + statusCodes);
    }
}
