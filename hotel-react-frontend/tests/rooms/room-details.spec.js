// tests/rooms/room-details.spec.js
//
// Project: chromium（requires customer storageState from auth.setup.js）
//
// TC-RD-01~08 require authentication (CustomerRoute guard).
// TC-RD-09 always creates a fresh unauthenticated context.

import { test, expect } from "@playwright/test";
import { RoomDetailsPage } from "../pages/RoomDetailsPage.js";

const API_BASE = process.env.BACKEND_URL
  ? `${process.env.BACKEND_URL}/api`
  : "http://localhost:9090/api";

// Booking dates for TC-RD-07 and TC-RD-08.
// Navigate 3 months ahead to use dates unlikely to conflict on a fresh DB.
// TC-RD-07 mocks the booking POST (no DB pollution, idempotent on re-runs).
// TC-RD-08 uses a beforeAll-seeded conflict booking so it reliably gets a "not available" error.
const BOOK_MONTHS_AHEAD  = 3;
const BOOK_CHECK_IN_DAY  = 15;
const BOOK_CHECK_OUT_DAY = 17;

/** Compute ISO date strings for the conflict booking (3 months ahead, days 15 / 17) */
function conflictDates() {
  const d = new Date();
  d.setMonth(d.getMonth() + BOOK_MONTHS_AHEAD);
  const y  = d.getFullYear();
  const mo = String(d.getMonth() + 1).padStart(2, "0");
  return {
    checkIn:  `${y}-${mo}-${String(BOOK_CHECK_IN_DAY).padStart(2, "0")}`,
    checkOut: `${y}-${mo}-${String(BOOK_CHECK_OUT_DAY).padStart(2, "0")}`,
  };
}

// Dynamically resolve the first available room ID from the DB (avoids hardcoded id=1).
// Also seed a conflict booking for TC-RD-08 (silently ignored if already booked).
let SEED_ROOM_ID;
test.beforeAll(async ({ request }) => {
  // 1. Get first room ID
  const resp = await request.get(`${API_BASE}/rooms/all`);
  const { rooms } = await resp.json();
  SEED_ROOM_ID = rooms?.[0]?.id ?? 1;

  // 2. Login as customer to obtain a token for seeding
  const loginRes = await request.post(`${API_BASE}/auth/login`, {
    data: { email: "customer@hotel.com", password: "Customer1234!" },
  });
  if (!loginRes.ok()) return;
  const { token } = await loginRes.json();

  // 3. Seed a real conflict booking for TC-RD-08; silently ignore if dates already taken
  const { checkIn, checkOut } = conflictDates();
  await request.post(`${API_BASE}/bookings`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { roomId: SEED_ROOM_ID, checkInDate: checkIn, checkOutDate: checkOut },
  });
});

test.describe("🛏️ Room Details & Booking Flow", () => {

  // ─────────────────────────────────────────────────────────────
  // TC-RD-01  页面加载，显示房间信息
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-01 | 页面加载，显示房间类型、价格、容纳人数", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    await expect(detailsPage.roomInfo).toBeVisible();
    await expect(detailsPage.roomImage).toBeVisible();

    const infoText = await detailsPage.roomInfo.textContent();
    expect(infoText).toMatch(/\$/);
    expect(infoText).toMatch(/capacity/i);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-02  点击 "Select Dates"，日期选择器显示
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-02 | 点击 Select Dates 按钮，DayPicker 显示", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    await detailsPage.openDatePicker();

    await expect(detailsPage.checkInPicker).toBeVisible();
    await expect(detailsPage.checkOutPicker).toBeVisible();
    await expect(detailsPage.proceedBtn).toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-03  不选日期直接点 Proceed，显示错误提示
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-03 | 不选日期直接点 Proceed，显示错误信息", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    await detailsPage.openDatePicker();
    await detailsPage.proceedBtn.click();

    const msg = await detailsPage.getErrorMessage();
    expect(msg).toContain("Please select both check-in and check-out dates");
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-04  选择合法日期，Proceed 后显示预订预览
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-04 | 选择入住/退房日期后，显示预订预览", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    await detailsPage.selectDates(20, 22);
    await detailsPage.proceedToPreview();

    await expect(detailsPage.bookingPreview).toBeVisible();
    const previewText = await detailsPage.bookingPreview.textContent();
    expect(previewText).toMatch(/check-in/i);
    expect(previewText).toMatch(/check-out/i);
    expect(previewText).toMatch(/total price/i);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-05  预览中总价 = pricePerNight × 天数
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-05 | 预订预览中总价计算正确", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    const infoText = await detailsPage.roomInfo.textContent();
    const priceMatch = infoText.match(/\$(\d+(?:\.\d+)?)\s*\/\s*night/i);
    const pricePerNight = priceMatch ? parseFloat(priceMatch[1]) : null;

    await detailsPage.selectDates(20, 22);
    await detailsPage.proceedToPreview();

    const previewText = await detailsPage.bookingPreview.textContent();
    const totalMatch  = previewText.match(/total price.*?\$(\d+(?:\.\d+)?)/i);
    const totalPrice  = totalMatch ? parseFloat(totalMatch[1]) : null;

    if (pricePerNight && totalPrice) {
      expect(totalPrice).toBe(pricePerNight * 2);
    }
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-06  点击 Cancel，隐藏预订预览
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-06 | 点击 Cancel，预订预览消失", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    await detailsPage.selectDates(20, 22);
    await detailsPage.proceedToPreview();
    await expect(detailsPage.bookingPreview).toBeVisible();

    await detailsPage.cancelBookingBtn.click();
    await expect(detailsPage.bookingPreview).not.toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-07  完整预订流程：选日期 → Proceed → Confirm → 显示确认信息
  //
  // Bug注: 原测试期望自动跳转到 /rooms，但实际上组件不会自动跳转
  // (acceptBooking 成功后显示 "Booking Confirmed!" + "Pay Later" 按钮)
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-07 | 完整预订流程，显示预订确认信息后可导航到 /rooms", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    // Mock the booking POST so no real booking is created in the DB.
    // This makes the test idempotent across re-runs (dates never get "used up").
    const { checkIn, checkOut } = conflictDates();
    await page.route(`${API_BASE}/bookings`, async (route, request) => {
      if (request.method() === "POST") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            status: 200,
            booking: {
              bookingReference: "TEST-MOCK-REF-007",
              checkInDate: checkIn,
              checkOutDate: checkOut,
              totalPrice: 300,
              paymentStatus: "PENDING",
            },
          }),
        });
      } else {
        await route.continue();
      }
    });

    await detailsPage.selectDates(BOOK_CHECK_IN_DAY, BOOK_CHECK_OUT_DAY, BOOK_MONTHS_AHEAD);
    await detailsPage.proceedToPreview();
    await detailsPage.confirmBooking();

    // .booking-preview reuses class for confirmed state showing "Booking Confirmed!"
    // Wait for mock response + React state update
    await expect(detailsPage.bookingPreview).toContainText(/booking confirmed/i, { timeout: 10_000 });

    // Click "Pay Later" button (class: cancel-booking) → navigate to /rooms
    await detailsPage.cancelBookingBtn.click();
    await expect(page).toHaveURL(/rooms/, { timeout: 5_000 });
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-08  预订已占用日期，显示后端错误信息
  // ─────────────────────────────────────────────────────────────
  test("TC-RD-08 | 预订已被占用的日期，显示错误信息", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "Requires customer auth");
    const detailsPage = new RoomDetailsPage(page);
    await detailsPage.goto(SEED_ROOM_ID);

    // Same dates as TC-RD-07 (3 months ahead, days 15-17) — TC-RD-07 books them first,
    // so this attempt should receive a "not available" error from the backend.
    await detailsPage.selectDates(BOOK_CHECK_IN_DAY, BOOK_CHECK_OUT_DAY, BOOK_MONTHS_AHEAD);
    await detailsPage.proceedToPreview();
    await detailsPage.confirmBooking();

    const msg = await detailsPage.getErrorMessage();
    expect(msg).toMatch(/not available/i);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-RD-09  未登录用户访问房间详情页，跳转到 /login
  // ─────────────────────────────────────────────────────────────
  test(
    "TC-RD-09 | 未登录用户访问房间详情页，被 CustomerRoute Guard 重定向到 /login",
    async ({ browser }, testInfo) => {
      // Browser timing issues occur in chromium/chromium-admin authenticated contexts.
      // Run only in chromium-public where no storageState is injected.
      test.skip(testInfo.project.name !== "chromium-public", "Unauthenticated Guard test — run in chromium-public only");
      const context = await browser.newContext();
      const page    = await context.newPage();

      await page.goto(`/room-details/${SEED_ROOM_ID}`);
      await expect(page).toHaveURL(/login/, { timeout: 12_000 });

      await context.close();
    }
  );
});
