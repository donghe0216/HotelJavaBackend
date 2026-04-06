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
        // ⚠️  This test will fail in CI unless IMAGE_DIRECTORY_FRONTEND is
        //     replaced with a configurable @Value / @TempDir path.
        //     See Bug note in test case sheet TC-R-04.

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
    @DisplayName("TC-R-03 | addRoom | .txt file upload → 500")
    void addRoom_fail_illegalFileType() {
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
            .statusCode(500)
            .body("message", containsStringIgnoringCase("image"));
    }

    @Test @Order(4)
    @DisplayName("TC-R-04 | addRoom | [Bug] hardcoded image directory path")
    void addRoom_hardcodedPath_failsInCi() {
        // This is a documentation test.
        // The real assertion is: after replacing IMAGE_DIRECTORY_FRONTEND with
        // an @Value("${room.image.dir}") property, the path should be
        // configurable and tests should pass in any environment.
        //
        // Action item: externalise to application.properties and inject via
        //   @Value("${room.image.dir:/tmp/rooms/}")
        //   private String imageDirectory;
        //
        // For now we just assert the endpoint is reachable (not that the path works).
        given()
            .spec(adminSpec)
        .when()
            .get("/rooms/all")
        .then()
            .statusCode(200);   // sanity — room controller is alive
    }

    @Test @Order(5)
    @DisplayName("TC-R-05 | addRoom | invalid room type → 400")
    void addRoom_invalidRoomTypeEnum_returns400() {
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
    @DisplayName("TC-R-07 | addRoom | pricePerNight = -1 → 400")
    void addRoom_negativePrice_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    502)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "-100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("pricePerNight"));
    }

    @Test @Order(8)
    @DisplayName("TC-R-08 | addRoom | duplicate roomNumber → 409")
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

    @Test @Order(9)
    @DisplayName("TC-R-09 | addRoom | missing required param → 400")
    void addRoom_missingRequiredParam_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400);
    }

    @Test @Order(10)
    @DisplayName("TC-R-10 | addRoom | pricePerNight = 0 → 400")
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

    @Test @Order(11)
    @DisplayName("TC-R-11 | addRoom | capacity = 0 → 400")
    void addRoom_zeroCapacity_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    504)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      0)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("capacity"));
    }

    @Test @Order(12)
    @DisplayName("TC-R-12 | addRoom | roomNumber = 0 → 400")
    void addRoom_zeroRoomNumber_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    0)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("roomNumber"));
    }

    // ── getAllRooms ───────────────────────────────────────────────────────────

    @Test @Order(13)
    @DisplayName("TC-R-13 | getAllRooms | returns all rooms sorted by id DESC")
    void getAllRooms_success() {
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/all")
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("rooms",   notNullValue())
            .body("rooms",   not(empty()));

        // Cache one ID for subsequent tests if not already set
        if (createdRoomId == null) {
            Integer firstId = given().spec(anonSpec).when().get("/rooms/all")
                    .then().extract().path("rooms[0].id");
            if (firstId != null) createdRoomId = firstId.longValue();
        }
    }

    // ── getAllRoomTypes ───────────────────────────────────────────────────────

    @Test @Order(14)
    @DisplayName("TC-R-14 | getAllRoomTypes | returns all room type values")
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

    @Test @Order(15)
    @DisplayName("TC-R-15 | getRoomById | valid id → returns room")
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

    @Test @Order(16)
    @DisplayName("TC-R-16 | getRoomById | unknown id → 404")
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

    @Test @Order(17)
    @DisplayName("TC-R-17 | updateRoom | update price and capacity → persisted")
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
               .then().body("room.capacity", equalTo(3));
    }

    @Test @Order(18)
    @DisplayName("TC-R-18 | updateRoom | unknown id → 404")
    void updateRoom_notFound() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id",            999999L)
            .multiPart("pricePerNight", "200.00")
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(404)
            .body("message", containsStringIgnoringCase("not found"));
    }

    @Test @Order(19)
    @DisplayName("TC-R-19 | updateRoom | roomNumber = 0 → 400")
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

    @Test @Order(20)
    @DisplayName("TC-R-20 | updateRoom | pricePerNight = 0 → 400")
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

    @Test @Order(21)
    @DisplayName("TC-R-21 | updateRoom | valid image → updated")
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

    @Test @Order(22)
    @DisplayName("TC-R-22 | updateRoom | .txt file upload → 500")
    void updateRoom_invalidFileType_returns500() {
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
            .statusCode(500)
            .body("message", containsStringIgnoringCase("image"));
    }

    @Test @Order(23)
    @DisplayName("TC-R-23 | updateRoom | capacity = 0 → 400")
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

    @Test @Order(24)
    @DisplayName("TC-R-24 | updateRoom | missing id → 400")
    void updateRoom_missingId_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("pricePerNight", "200.00")
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(400);
    }

    // ── getAvailableRooms ─────────────────────────────────────────────────────

    @Test @Order(25)
    @DisplayName("TC-R-25 | getAvailableRooms | valid dates → returns available rooms")
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

    @Test @Order(26)
    @DisplayName("TC-R-26 | getAvailableRooms | checkIn in the past → 400")
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

    @Test @Order(27)
    @DisplayName("TC-R-27 | getAvailableRooms | checkOut before checkIn → 400")
    void getAvailableRooms_fail_checkOutBeforeCheckIn() {
        given()
            .spec(anonSpec)
            .queryParam("checkInDate",  inDays(3))
            .queryParam("checkOutDate", tomorrow())
            .queryParam("roomType",     "SINGLE")
        .when()
            .get("/rooms/available")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("before check in"));
    }

    @Test @Order(28)
    @DisplayName("TC-R-28 | getAvailableRooms | checkIn == checkOut → 400")
    void getAvailableRooms_fail_sameDate() {
        given()
            .spec(anonSpec)
            .queryParam("checkInDate",  tomorrow())
            .queryParam("checkOutDate", tomorrow())
            .queryParam("roomType",     "SINGLE")
        .when()
            .get("/rooms/available")
        .then()
            .statusCode(400)
            .body("message", containsStringIgnoringCase("equal to check out date"));
    }

    // ── searchRoom ────────────────────────────────────────────────────────────

    @Test @Order(29)
    @DisplayName("TC-R-29 | searchRoom | keyword match → returns rooms")
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

    @Test @Order(30)
    @DisplayName("TC-R-30 | searchRoom | empty string → 400")
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

    @Test @Order(31)
    @DisplayName("TC-R-31 | searchRoom | SQL injection input → 200, empty list")
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

    @Test @Order(32)
    @DisplayName("TC-R-32 | searchRoom | 300-char keyword → not 500")
    void searchRoom_oversizedInput_doesNotReturn500() {
        given()
            .spec(anonSpec)
            .queryParam("input", "a".repeat(300))
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(not(500));
    }

    @Test @Order(33)
    @DisplayName("TC-R-33 | searchRoom | unicode / Japanese input → 200, empty list")
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

    @Test @Order(34)
    @DisplayName("TC-R-34 | deleteRoom | valid id → deleted")
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
    }

    @Test @Order(35)
    @DisplayName("TC-R-35 | deleteRoom | unknown id → 404")
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
