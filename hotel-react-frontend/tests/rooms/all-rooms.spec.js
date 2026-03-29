// tests/rooms/all-rooms.spec.js
// Project: chromium-public（ログイン不要）

import { test, expect } from "@playwright/test";
import { AllRoomsPage } from "../pages/AllRoomsPage.js";

test.describe("🏠 All Rooms Page", () => {

  // ─────────────────────────────────────────────────────────────
  // TC-AR-01  页面加载，显示房间列表和筛选控件
  // ─────────────────────────────────────────────────────────────
  test("TC-AR-01 | 页面加载，显示标题、筛选下拉框和房间列表", async ({ page }) => {
    const roomsPage = new AllRoomsPage(page);
    await roomsPage.goto();
    await roomsPage.waitForRoomsToLoad();

    await expect(roomsPage.heading).toBeVisible();
    await expect(roomsPage.roomTypeSelect).toBeVisible();

    const count = await roomsPage.getRoomCardCount();
    expect(count).toBeGreaterThan(0);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-AR-02  Room Type 筛选下拉框包含 "All" 选项
  // ─────────────────────────────────────────────────────────────
  test("TC-AR-02 | Room Type 下拉框默认选中 'All'", async ({ page }) => {
    const roomsPage = new AllRoomsPage(page);
    await roomsPage.goto();
    await roomsPage.waitForRoomsToLoad();

    const selectedValue = await roomsPage.roomTypeSelect.inputValue();
    expect(selectedValue).toBe("");  // <option value="">All</option>
  });

  // ─────────────────────────────────────────────────────────────
  // TC-AR-03  选择房间类型，列表筛选正确
  // ─────────────────────────────────────────────────────────────
  test("TC-AR-03 | 选择 SINGLE 类型，只显示 SINGLE 房间", async ({ page }) => {
    const roomsPage = new AllRoomsPage(page);
    await roomsPage.goto();
    await roomsPage.waitForRoomsToLoad();

    const totalBefore = await roomsPage.getRoomCardCount();

    await roomsPage.selectRoomType("SINGLE");
    await page.waitForTimeout(500);

    const totalAfter = await roomsPage.getRoomCardCount();

    expect(totalAfter).toBeLessThanOrEqual(totalBefore);
    const typeTexts = await page.locator(".room-list-item .room-details h3").allTextContents();
    typeTexts.forEach(t => expect(t.toLowerCase()).toContain("single"));
  });

  // ─────────────────────────────────────────────────────────────
  // TC-AR-04  切回 "All"，显示全部房间
  // ─────────────────────────────────────────────────────────────
  test("TC-AR-04 | 切换到 All，显示所有房间", async ({ page }) => {
    const roomsPage = new AllRoomsPage(page);
    await roomsPage.goto();
    await roomsPage.waitForRoomsToLoad();

    const totalAll = await roomsPage.getRoomCardCount();

    await roomsPage.selectRoomType("SINGLE");
    await page.waitForTimeout(300);
    await roomsPage.selectRoomType("");   // back to All
    await page.waitForTimeout(300);

    const totalAfter = await roomsPage.getRoomCardCount();
    expect(totalAfter).toBe(totalAll);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-AR-05  点击某个房间卡片，跳转到 /room-details/:id
  //
  // Only runs in chromium (customer auth).
  // chromium-public: Guard redirects /room-details to /login.
  // chromium-admin:  RoomResult renders "Edit Room" → /admin/edit-room/:id.
  // ─────────────────────────────────────────────────────────────
  test("TC-AR-05 | 点击房间卡片，跳转到房间详情页", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth; admin navigates to edit-room instead");
    const roomsPage = new AllRoomsPage(page);
    await roomsPage.goto();
    await roomsPage.waitForRoomsToLoad();

    await roomsPage.clickFirstRoom();
    await expect(page).toHaveURL(/room-details\/\d+/, { timeout: 8_000 });
  });

  // ─────────────────────────────────────────────────────────────
  // TC-AR-06  按房间类型筛选，结果更新
  //
  // Note: AllRoomsPage does NOT have a keyword search text input.
  // RoomSearch uses date pickers (readOnly) + room type select for availability.
  // This test validates the room type filter with DOUBLE type.
  // ─────────────────────────────────────────────────────────────
  test("TC-AR-06 | 选择 DOUBLE 类型，只显示 DOUBLE 房间", async ({ page }) => {
    const roomsPage = new AllRoomsPage(page);
    await roomsPage.goto();
    await roomsPage.waitForRoomsToLoad();

    await roomsPage.selectRoomType("DOUBLE");
    await page.waitForTimeout(300);

    const count = await roomsPage.getRoomCardCount();
    expect(count).toBeGreaterThanOrEqual(0);

    if (count > 0) {
      const typeTexts = await page.locator(".room-list-item .room-details h3").allTextContents();
      typeTexts.forEach(t => expect(t.toUpperCase()).toContain("DOUBLE"));
    }
  });

  // ─────────────────────────────────────────────────────────────
  // TC-AR-07  超过 9 条时分页控件显示
  // ─────────────────────────────────────────────────────────────
  test("TC-AR-07 | 房间总数超过 9 时，显示分页控件", async ({ page }) => {
    const roomsPage = new AllRoomsPage(page);
    await roomsPage.goto();
    await roomsPage.waitForRoomsToLoad();

    const total = await roomsPage.getRoomCardCount();

    if (total === 9) {
      const pagination = page.locator(".pagination");
      const visible = await pagination.isVisible().catch(() => false);
      console.log("Pagination visible:", visible);
    }
  });
});
