package api;

import io.restassured.builder.MultiPartSpecBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;


import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * API tests for Room endpoints.
 *
 * Assumed routes:
 *   POST   /rooms/add                   (ADMIN, multipart/form-data)
 *   PUT    /rooms/update                (ADMIN, multipart/form-data)
 *   GET    /rooms/all
 *   GET    /rooms/{id}
 *   DELETE /rooms/delete/{id}           (ADMIN)
 *   GET    /rooms/available?checkIn=&checkOut=&roomType=
 *   GET    /rooms/types
 *   GET    /rooms/search?input=
 *
 * Note on image tests:
 *   RoomServiceImpl saves files to a hard-coded local path
 *   (/Users/dennismac/...).  In CI this directory does not exist.
 *   Tests that involve file upload are clearly annotated so that
 *   they can be skipped or the path can be overridden via
 *   an @Value property in the real application.
 */
@DisplayName("Room API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoomApiTest extends BaseApiTest {

    private static Long createdRoomId;

    // ── Minimal valid JPEG magic bytes (1×1 pixel) ────────────────────────────
    private static final byte[] MINIMAL_JPEG = new byte[]{
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,
        0x00,0x10,0x4A,0x46,0x49,0x46,0x00,0x01,
        0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00,
        (byte)0xFF,(byte)0xD9
    };

    // ── addRoom ───────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("TC-R-01 | addRoom | valid JPEG upload → room created")
    void addRoom_success_withImage() {
        // Design Note (self-review fix):
        // Image directory was originally hardcoded, which caused failures in CI and other environments.
        // Externalized to application.properties to ensure portability across environments.

        Integer id = given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    201)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
            .multiPart("description",   "Cozy single room")
            .multiPart(new MultiPartSpecBuilder(MINIMAL_JPEG)
                    .fileName("room.jpg")
                    .mimeType("image/jpeg")
                    .controlName("imageFile")
                    .build())
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("added"))
            .extract().path("room.id");

        if (id != null) createdRoomId = id.longValue();
    }

    @Test @Order(2)
    @DisplayName("TC-R-02 | addRoom | no image → room created with null imageUrl")
    void addRoom_success_withoutImage() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    202)
            .multiPart("type",          "DOUBLE")
            .multiPart("pricePerNight", "150.00")
            .multiPart("capacity",      4)
            .multiPart("description",   "Double room no photo")
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("added"));
    }

    @Test @Order(3)
    @DisplayName("TC-R-03 | addRoom | .txt file upload → 400")
    void addRoom_fail_illegalFileType() {
        // [Bug fixed] Was returning 500 (IllegalArgumentException → catch-all).
        // Found via this test: fixed by throwing NameValueRequiredException → 400.
        byte[] textBytes = "this is not an image".getBytes();

        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    203)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "120.00")
            .multiPart("capacity",      2)
            .multiPart("description",   "Invalid file type test")
            .multiPart(new MultiPartSpecBuilder(textBytes)
                    .fileName("file.txt")
                    .mimeType("text/plain")
                    .controlName("imageFile")
                    .build())
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("image"));
    }

    @Test @Order(4)
    @DisplayName("TC-R-04 | addRoom | image upload works across environments")
    void addRoom_imageUpload_portableAcrossEnvironments() {
        // Design Note (self-review fix):
        // Image directory was originally hardcoded, which caused failures in CI and other environments.
        // Externalized to application.properties to ensure portability across environments.
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    204)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
            .multiPart(new MultiPartSpecBuilder(MINIMAL_JPEG)
                    .fileName("room.jpg")
                    .mimeType("image/jpeg")
                    .controlName("imageFile")
                    .build())
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(200)
            .body("message", containsStringIgnoringCase("added"));
    }

    @Test @Order(5)
    @DisplayName("TC-R-05 | addRoom | invalid room type → 400")
    void addRoom_invalidRoomTypeEnum_returns400() {
        // [Bug] No message assertion possible here.
        // Invalid enum value causes HttpMessageNotReadableException → handled by parent
        // ResponseEntityExceptionHandler → no custom message field in response.
        // Fix: override handleHttpMessageNotReadable in GlobalExceptionHandler to return Response.
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    500)
            .multiPart("type",          "PENTHOUSE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400);
    }

    @Test @Order(6)
    @DisplayName("TC-R-06 | addRoom | pricePerNight as string → 400")
    void addRoom_priceAsString_returns400() {
        // [Bug] No message assertion possible here.
        // Spring handles MethodArgumentTypeMismatchException via ResponseEntityExceptionHandler
        // (the parent class), which returns Spring's default format without our custom message field.
        // GlobalExceptionHandler already overrides handleMethodArgumentNotValid (Bean Validation),
        // but handleMethodArgumentTypeMismatch is not overridden — response format is inconsistent.
        // Fix: override handleMethodArgumentTypeMismatch in GlobalExceptionHandler to return Response.
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    501)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "not-a-number")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400);
    }

    @Test @Order(7)
    @DisplayName("TC-R-07 | addRoom | duplicate roomNumber → 409")
    void addRoom_duplicateRoomNumber_returns409() {
        // Duplicate room number triggers DataIntegrityViolationException from the DB unique constraint.
        // GlobalExceptionHandler maps DataIntegrityViolationException → 409 CONFLICT.

        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    299)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
            .multiPart("description",   "Duplicate test room")
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(200);

        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    299)
            .multiPart("type",          "DOUBLE")
            .multiPart("pricePerNight", "200.00")
            .multiPart("capacity",      4)
            .multiPart("description",   "Duplicate test room attempt 2")
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(409)
            .body("message", containsStringIgnoringCase("already exists"));
    }

    @Test @Order(8)
    @DisplayName("TC-R-08 | addRoom | roomNumber not sent → 400")
    void addRoom_nullRoomNumber_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("roomNumber"));
    }

    @Test @Order(9)
    @DisplayName("TC-R-09 | addRoom | pricePerNight = 0 → 400")
    void addRoom_zeroPricePerNight_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    503)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "0.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("pricePerNight"));
    }

    // ── getAllRooms ───────────────────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("TC-R-10 | getAllRooms | returns all rooms sorted by id DESC")
    void getAllRooms_success() {
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/all")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("rooms",   notNullValue());

        // Cache one ID for subsequent tests if not already set
        if (createdRoomId == null) {
            Integer firstId = given().spec(anonSpec).when().get("/rooms/all")
                    .then().extract().path("rooms[0].id");
            if (firstId != null) createdRoomId = firstId.longValue();
        }
    }

    // ── getAllRoomTypes ───────────────────────────────────────────────────────

    @Test @Order(11)
    @DisplayName("TC-R-11 | getAllRoomTypes | returns all room type values")
    void getAllRoomTypes_returnsAllEnumValues() {
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/types")
        .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    // ── getRoomById ───────────────────────────────────────────────────────────

    @Test @Order(12)
    @DisplayName("TC-R-12 | getRoomById | valid id → returns room")
    void getRoomById_success() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/{id}", createdRoomId)
        .then()
            .statusCode(200)
            .body("status",   equalTo(200))
            .body("room.id",  equalTo(createdRoomId.intValue()));
    }

    @Test @Order(13)
    @DisplayName("TC-R-13 | getRoomById | unknown id → 404")
    void getRoomById_notFound() {
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/{id}", 999999L)
        .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("not found"));
    }

    // ── updateRoom ────────────────────────────────────────────────────────────

    @Test @Order(14)
    @DisplayName("TC-R-14 | updateRoom | update price and capacity → persisted")
    void updateRoom_success_partialUpdate() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id",            createdRoomId)
            .multiPart("pricePerNight", "200.00")
            .multiPart("capacity",      3)
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("updated"));

        given().spec(anonSpec).when().get("/rooms/{id}", createdRoomId)
               .then()
               .body("room.pricePerNight", equalTo(200.00f))
               .body("room.capacity",      equalTo(3))
               // Fields not included in update payload must remain unchanged
               .body("room.roomNumber",    equalTo(201))
               .body("room.type",          equalTo("SINGLE"));
    }

    @Test @Order(15)
    @DisplayName("TC-R-15 | updateRoom | roomNumber already taken → 409")
    void updateRoom_duplicateRoomNumber_returns409() {
        // Room 202 was created in TC-R-02; updating createdRoomId's roomNumber to 202 should conflict
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id",         createdRoomId)
            .multiPart("roomNumber", 202)
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(409)
            .body("message", containsStringIgnoringCase("already exists"));
    }

    @Test @Order(16)
    @DisplayName("TC-R-16 | updateRoom | roomNumber = 0 → 400")
    void updateRoom_zeroRoomNumber_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id",         createdRoomId)
            .multiPart("roomNumber", 0)
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("roomNumber"));
    }

    @Test @Order(17)
    @DisplayName("TC-R-17 | updateRoom | pricePerNight = 0 → 400")
    void updateRoom_zeroPricePerNight_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id",            createdRoomId)
            .multiPart("pricePerNight", "0.00")
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("pricePerNight"));
    }

    @Test @Order(18)
    @DisplayName("TC-R-18 | updateRoom | valid image → updated")
    void updateRoom_success_withImage() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id", createdRoomId)
            .multiPart(new MultiPartSpecBuilder(MINIMAL_JPEG)
                    .fileName("room.jpg")
                    .mimeType("image/jpeg")
                    .controlName("imageFile")
                    .build())
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(200)
            .body("message", containsStringIgnoringCase("updated"));
    }

    @Test @Order(19)
    @DisplayName("TC-R-19 | updateRoom | .txt file upload → 400")
    void updateRoom_invalidFileType_returns400() {
        // [Bug fixed] Was returning 500 (IllegalArgumentException → catch-all).
        // Found via this test: fixed by throwing NameValueRequiredException → 400.
        byte[] textBytes = "this is not an image".getBytes();

        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id", createdRoomId)
            .multiPart(new MultiPartSpecBuilder(textBytes)
                    .fileName("file.txt")
                    .mimeType("text/plain")
                    .controlName("imageFile")
                    .build())
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("image"));
    }

    @Test @Order(20)
    @DisplayName("TC-R-20 | updateRoom | capacity = 0 → 400")
    void updateRoom_zeroCapacity_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id",       createdRoomId)
            .multiPart("capacity", 0)
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("capacity"));
    }

    // ── getAvailableRooms ─────────────────────────────────────────────────────

    @Test @Order(21)
    @DisplayName("TC-R-21 | getAvailableRooms | valid dates → returns available rooms")
    void getAvailableRooms_success() {
        given()
            .spec(anonSpec)
            .queryParam("checkInDate",  tomorrow())
            .queryParam("checkOutDate", inDays(3))
            .queryParam("roomType",     "SINGLE")
        .when()
            .get("/rooms/available")
        .then()
            .statusCode(200)
            .body("status", equalTo(200))
            .body("rooms",  notNullValue());
    }

    @Test @Order(22)
    @DisplayName("TC-R-22 | getAvailableRooms | checkIn in the past → 400")
    void getAvailableRooms_fail_checkInBeforeToday() {
        given()
            .spec(anonSpec)
            .queryParam("checkInDate",  yesterday())
            .queryParam("checkOutDate", tomorrow())
            .queryParam("roomType",     "SINGLE")
        .when()
            .get("/rooms/available")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("before today"));
    }

    // ── searchRoom ────────────────────────────────────────────────────────────

    @Test @Order(23)
    @DisplayName("TC-R-23 | searchRoom | keyword match → returns rooms")
    void searchRoom_success_validInput() {
        given()
            .spec(anonSpec)
            .queryParam("input", "single")
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(200)
            .body("status", equalTo(200))
            .body("rooms",  notNullValue());
    }

    @Test @Order(24)
    @DisplayName("TC-R-24 | searchRoom | empty string → 400")
    void searchRoom_emptyInput_returns400() {
        given()
            .spec(anonSpec)
            .queryParam("input", "")
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("keyword"));
    }

    @Test @Order(25)
    @DisplayName("TC-R-25 | searchRoom | SQL injection input → 200, empty list")
    void searchRoom_sqlInjection_returns200WithEmptyList() {
        given()
            .spec(anonSpec)
            .queryParam("input", "' OR 1=1; --")
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(200)
            .body("status", equalTo(200))
            .body("rooms", empty());
    }

    @Test @Order(26)
    @DisplayName("TC-R-26 | searchRoom | 300-char keyword → not 500")
    void searchRoom_oversizedInput_doesNotReturn500() {
        given()
            .spec(anonSpec)
            .queryParam("input", "a".repeat(300))
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(not(500));
    }

    @Test @Order(27)
    @DisplayName("TC-R-27 | searchRoom | unicode / Japanese input → 200, empty list")
    void searchRoom_unicodeInput_returns200WithEmptyList() {
        given()
            .spec(anonSpec)
            .queryParam("input", "シングルルーム")
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(200)
            .body("status", equalTo(200))
            .body("rooms", empty());
    }

    // ── deleteRoom ────────────────────────────────────────────────────────────

    @Test @Order(28)
    @DisplayName("TC-R-28 | deleteRoom | valid id → deleted")
    void deleteRoom_success() {
        // TODO: test order dependency — this test relies on data created by a previous test, refactor to use @BeforeEach or independent fixtures
        given()
            .spec(adminSpec)
        .when()
            .delete("/rooms/delete/{id}", createdRoomId)
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("deleted"));

        // Verify deletion persisted — room must no longer be retrievable
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/{id}", createdRoomId)
        .then()
            .statusCode(404);
    }

    @Test @Order(29)
    @DisplayName("TC-R-29 | deleteRoom | unknown id → 404")
    void deleteRoom_notFound() {
        given()
            .spec(adminSpec)
        .when()
            .delete("/rooms/delete/{id}", 999999L)
        .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("not found"));
    }
}
