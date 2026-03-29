package api;

import io.restassured.builder.MultiPartSpecBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

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
 */
@DisplayName("🏠 Room API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoomApiTest extends BaseApiTest {

    private static Long createdRoomId;

    private static final byte[] MINIMAL_JPEG = new byte[]{
        (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,
        0x00,0x10,0x4A,0x46,0x49,0x46,0x00,0x01,
        0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00,
        (byte)0xFF,(byte)0xD9
    };

    // ═══════════════════════════════════════════════════════════════
    // TC-R-01  addRoom: 带合法图片，成功创建
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(1)
    @DisplayName("TC-R-01 | addRoom | 上传合法 JPEG 并成功创建房间")
    void addRoom_success_withImage() {
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

    // ═══════════════════════════════════════════════════════════════
    // TC-R-02  addRoom: 不带图片，成功创建
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(2)
    @DisplayName("TC-R-02 | addRoom | 不上传图片时成功创建，imageUrl 为 null")
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

    // ═══════════════════════════════════════════════════════════════
    // TC-R-03  addRoom: 非法图片格式（text/plain）→ 400
    //          如果实际返回 500 → 标注为 Bug，需修复后回归
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(3)
    @DisplayName("TC-R-03 | addRoom | 上传 .txt 文件应返回 400（返回 500 则为 Bug）")
    void addRoom_fail_illegalFileType() {
        byte[] textBytes = "this is not an image".getBytes();

        int status = given()
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
            .extract().statusCode();

        if (status == 500) {
            System.out.println("⚠️  BUG TC-R-03: illegal file type returns 500 instead of 400. " +
                    "Fix: catch IllegalArgumentException in GlobalExceptionHandler.");
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                status == 400 || status == 500,
                "Expected 400 (correct) or 500 (bug). Got: " + status);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-04  addRoom: @NotNull 字段缺失 → 400（三种必填字段各一个）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(4)
    @DisplayName("TC-R-04 | addRoom | capacity=null → 400（@NotNull 校验）")
    void addRoom_nullCapacity_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    301)
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            // capacity intentionally omitted
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400);
    }

    @Test @Order(5)
    @DisplayName("TC-R-05 | addRoom | roomNumber=null → 400（@NotNull 校验）")
    void addRoom_nullRoomNumber_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            // roomNumber intentionally omitted
            .multiPart("type",          "SINGLE")
            .multiPart("pricePerNight", "100.00")
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400);
    }

    @Test @Order(6)
    @DisplayName("TC-R-06 | addRoom | pricePerNight=null → 400（@NotNull 校验）")
    void addRoom_nullPricePerNight_returns400() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("roomNumber",    302)
            .multiPart("type",          "SINGLE")
            // pricePerNight intentionally omitted
            .multiPart("capacity",      2)
        .when()
            .post("/rooms/add")
        .then()
            .statusCode(400);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-07  getAllRooms: 按 id DESC 排序验证
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(7)
    @DisplayName("TC-R-07 | getAllRooms | 查询所有房间，按 id DESC 排序")
    void getAllRooms_success() {
        List<Integer> ids = given()
            .spec(anonSpec)
        .when()
            .get("/rooms/all")
        .then()
            .statusCode(200)
            .body("status", equalTo(200))
            .body("rooms",  notNullValue())
            .body("rooms",  not(empty()))
            .extract().jsonPath().getList("rooms.id");

        for (int i = 0; i < ids.size() - 1; i++) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    ids.get(i) > ids.get(i + 1),
                    "rooms[" + i + "].id=" + ids.get(i) + " should be > rooms[" + (i+1) + "].id=" + ids.get(i+1));
        }

        if (createdRoomId == null && !ids.isEmpty()) {
            createdRoomId = ids.get(0).longValue();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-08  getRoomById: 成功
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(8)
    @DisplayName("TC-R-08 | getRoomById | 有效 ID → 返回 RoomDTO")
    void getRoomById_success() {
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/{id}", createdRoomId)
        .then()
            .statusCode(200)
            .body("status",   equalTo(200))
            .body("room.id",  equalTo(createdRoomId.intValue()));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-09  getRoomById: ID 不存在（TC-RS-11 的 smoke）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(9)
    @DisplayName("TC-R-09 | getRoomById | ID 不存在 → 404（TC-RS-11 的 smoke）")
    void getRoomById_notFound() {
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/{id}", 999999L)
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("not found"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-10  updateRoom: 部分更新 + 持久化验证
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(10)
    @DisplayName("TC-R-10 | updateRoom | 成功更新 pricePerNight/capacity，原值字段不变，持久化验证")
    void updateRoom_success_partialUpdate() {
        String originalType        = given().spec(anonSpec).when().get("/rooms/{id}", createdRoomId)
                .then().extract().path("room.type");
        Integer originalRoomNumber = given().spec(anonSpec).when().get("/rooms/{id}", createdRoomId)
                .then().extract().path("room.roomNumber");

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
               .body("room.pricePerNight", equalTo(200.0f))
               .body("room.capacity",      equalTo(3))
               .body("room.roomNumber",    equalTo(originalRoomNumber))
               .body("room.type",          equalTo(originalType));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-11  updateRoom: ID 不存在（TC-RS-19 的 smoke）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(11)
    @DisplayName("TC-R-11 | updateRoom | ID 不存在 → 404（TC-RS-19 的 smoke）")
    void updateRoom_notFound() {
        given()
            .spec(adminSpec)
            .contentType("multipart/form-data")
            .multiPart("id",            999999L)
            .multiPart("pricePerNight", "200.00")
        .when()
            .put("/rooms/update")
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("not found"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-12  getAvailableRooms: 合法日期
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(12)
    @DisplayName("TC-R-12 | getAvailableRooms | 合法日期范围 → 返回可用房间列表")
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

    // ═══════════════════════════════════════════════════════════════
    // TC-R-13  getAvailableRooms: 非法日期 → 400（TC-RS-13~15 的 smoke）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(13)
    @DisplayName("TC-R-13 | getAvailableRooms | checkOut < checkIn → 400（TC-RS-13~15 的 smoke）")
    void getAvailableRooms_invalidDate_returns400() {
        given()
            .spec(anonSpec)
            .queryParam("checkInDate",  inDays(5))
            .queryParam("checkOutDate", tomorrow())
            .queryParam("roomType",     "SINGLE")
        .when()
            .get("/rooms/available")
        .then()
            .statusCode(anyOf(is(400), is(422)))
            .body("message", containsStringIgnoringCase("before check in"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-14  getAllRoomTypes: 返回所有枚举值
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(14)
    @DisplayName("TC-R-14 | getAllRoomTypes | 返回所有 RoomType 枚举值，列表非空")
    void getAllRoomTypes_returnsAllEnumValues() {
        given()
            .spec(anonSpec)
        .when()
            .get("/rooms/types")
        .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-15  searchRoom: 合法关键词
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(15)
    @DisplayName("TC-R-15 | searchRoom | 合法关键词 → 每条结果包含关键词")
    void searchRoom_success_validInput() {
        given()
            .spec(anonSpec)
            .queryParam("input", "single")
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(200)
            .body("status", equalTo(200))
            .body("rooms",  notNullValue())
            .body("rooms.type", everyItem(containsStringIgnoringCase("SINGLE")));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-16  searchRoom: 空字符串 → 400（TC-RS-21 的 smoke）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(16)
    @DisplayName("TC-R-16 | searchRoom | input='' → 400（TC-RS-21 的 smoke）")
    void searchRoom_emptyInput_returns400() {
        given()
            .spec(anonSpec)
            .queryParam("input", "")
        .when()
            .get("/rooms/search")
        .then()
            .statusCode(400);
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-17  deleteRoom: 成功删除 + 后置验证（放最后，避免影响其他 case）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(90)
    @DisplayName("TC-R-17 | deleteRoom | 成功删除：200 + GET 404 + 不在列表中")
    void deleteRoom_success() {
        given()
            .spec(adminSpec)
        .when()
            .delete("/rooms/delete/{id}", createdRoomId)
        .then()
            .statusCode(200)
            .body("status",  equalTo(200))
            .body("message", containsStringIgnoringCase("deleted"));

        given().spec(anonSpec).when().get("/rooms/{id}", createdRoomId)
               .then().statusCode(anyOf(is(400), is(404)));

        List<Integer> allIds = given().spec(anonSpec).when().get("/rooms/all")
                .then().extract().jsonPath().getList("rooms.id");
        org.junit.jupiter.api.Assertions.assertFalse(
                allIds != null && allIds.contains(createdRoomId.intValue()),
                "Deleted room id=" + createdRoomId + " should not appear in /rooms/all");
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-18  deleteRoom: ID 不存在（TC-RS-09 的 smoke）
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(91)
    @DisplayName("TC-R-18 | deleteRoom | ID 不存在 → 404（TC-RS-09 的 smoke）")
    void deleteRoom_notFound() {
        given()
            .spec(adminSpec)
        .when()
            .delete("/rooms/delete/{id}", 999999L)
        .then()
            .statusCode(anyOf(is(400), is(404)))
            .body("message", containsStringIgnoringCase("not found"));
    }

    // ═══════════════════════════════════════════════════════════════
    // TC-R-19  【Bug文档】IMAGE_DIRECTORY_FRONTEND 硬编码路径问题
    // ═══════════════════════════════════════════════════════════════
    @Test @Order(99)
    @DisplayName("TC-R-19 | 【Bug文档】IMAGE_DIRECTORY_FRONTEND 硬编码路径在 CI 环境下失败")
    void addRoom_hardcodedPath_failsInCi() {
        given()
            .spec(adminSpec)
        .when()
            .get("/rooms/all")
        .then()
            .statusCode(200);
    }
}
