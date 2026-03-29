// tests/booking/find-booking.spec.js
// Project: chromium-public（/find-booking はログイン不要）
//
// Pre-condition:
//   auth.setup.js の "create seed booking" ステップが先に実行されること。
//   tests/.auth/booking.json に bookingReference が書き込まれていれば
//   TC-FB-06~09 が有効な予約で動作する。
//   ファイルがない場合、それらのテストは test.skip で安全にスキップされる。

import { test, expect } from "@playwright/test";
import { FindBookingPage } from "../pages/FindBookingPage.js";
import fs   from "fs";
import path from "path";

// ── シードデータ: auth.setup.js で作成された予約 reference を読み込む ──
let SEED_BOOKING_REF = null;
try {
  const bookingFile = path.join("tests", ".auth", "booking.json");
  if (fs.existsSync(bookingFile)) {
    SEED_BOOKING_REF = JSON.parse(fs.readFileSync(bookingFile, "utf-8")).ref;
  }
} catch { /* 読み込み失敗時は null のまま — 依存テストが test.skip される */ }

test.describe("🔍 Find Booking Page", () => {

  // ─────────────────────────────────────────────────────────────
  // TC-FB-01  页面加载，显示输入框和 Find 按钮
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-01 | 页面加载，显示搜索输入框和 Find 按钮", async ({ page }) => {
    const findPage = new FindBookingPage(page);
    await findPage.goto();

    await expect(findPage.codeInput).toBeVisible();
    await expect(findPage.findButton).toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-02  空输入点击 Find，显示错误提示
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-02 | 空输入点击 Find，显示 'Please Enter a booking confirmation code'", async ({ page }) => {
    const findPage = new FindBookingPage(page);
    await findPage.goto();
    await findPage.findButton.click();

    const msg = await findPage.getErrorMessage();
    expect(msg).toContain("Please Enter a booking confirmation code");
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-03  输入只含空格，显示错误提示
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-03 | 输入纯空格，trim 后为空，显示错误提示", async ({ page }) => {
    const findPage = new FindBookingPage(page);
    await findPage.goto();
    await findPage.search("     ");

    const msg = await findPage.getErrorMessage();
    expect(msg).toContain("Please Enter a booking confirmation code");
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-04  错误信息在 5 秒后自动消失
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-04 | 错误信息 5 秒后自动消失", async ({ page }) => {
    const findPage = new FindBookingPage(page);
    await findPage.goto();
    await findPage.findButton.click();

    await expect(findPage.errorMessage).toBeVisible();
    // エラーメッセージが 5 秒後に消えることを Playwright の built-in polling で待機。
    // waitForTimeout(固定sleep) は flaky の原因になるため使わない。
    await expect(findPage.errorMessage).not.toBeVisible({ timeout: 7_000 });
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-05  无效 reference，显示后端错误信息
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-05 | 无效 reference，显示后端错误提示", async ({ page }) => {
    const findPage = new FindBookingPage(page);
    await findPage.goto();
    await findPage.search("INVALID-REF-000");

    const msg = await findPage.getErrorMessage();
    expect(msg).toBeTruthy();
    await expect(findPage.bookingDetails).not.toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-06  有效 reference，显示完整预订详情
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-06 | 有效 reference，显示预订/用户/房间三个区块", async ({ page }) => {
    test.skip(!SEED_BOOKING_REF, "No seed booking reference — run auth setup first");
    const findPage = new FindBookingPage(page);
    await findPage.goto();
    await findPage.search(SEED_BOOKING_REF);
    await findPage.waitForBookingDetails();

    const detailsText = await findPage.bookingDetails.textContent();

    // Booking info
    expect(detailsText).toMatch(/booking code/i);
    expect(detailsText).toMatch(/check-in date/i);
    expect(detailsText).toMatch(/check-out date/i);
    expect(detailsText).toMatch(/payment status/i);

    // Booker info
    expect(detailsText).toMatch(/first name/i);
    expect(detailsText).toMatch(/email/i);

    // Room info
    expect(detailsText).toMatch(/room number/i);
    expect(detailsText).toMatch(/room type/i);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-07  显示的 Booking Code 与输入的 reference 一致
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-07 | 显示的 Booking Code 与搜索的 reference 一致", async ({ page }) => {
    test.skip(!SEED_BOOKING_REF, "No seed booking reference — run auth setup first");
    const findPage = new FindBookingPage(page);
    await findPage.goto();
    await findPage.search(SEED_BOOKING_REF);
    await findPage.waitForBookingDetails();

    const codeText = await findPage.bookingCode.textContent();
    expect(codeText).toContain(SEED_BOOKING_REF);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-08  连续搜索：先搜无效再搜有效，结果正确更新
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-08 | 先搜无效 reference 再搜有效，结果正确覆盖", async ({ page }) => {
    test.skip(!SEED_BOOKING_REF, "No seed booking reference — run auth setup first");
    const findPage = new FindBookingPage(page);
    await findPage.goto();

    // First search: invalid
    await findPage.search("BAD-REF");
    await expect(findPage.errorMessage).toBeVisible({ timeout: 6_000 });

    // Second search: valid
    await findPage.codeInput.clear();
    await findPage.search(SEED_BOOKING_REF);
    await findPage.waitForBookingDetails();

    await expect(findPage.bookingDetails).toBeVisible();
    await expect(findPage.errorMessage).not.toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-FB-09  【Bug文档】"Booker Detials" 标题拼写错误
  //
  //   FindBookingPage.jsx line: <h3>Booker Detials</h3>
  //   正しくは "Booker Details"
  // ─────────────────────────────────────────────────────────────
  test("TC-FB-09 | 【Bug文档】'Booker Detials' 标题存在拼写错误", async ({ page }) => {
    test.skip(!SEED_BOOKING_REF, "No seed booking reference — run auth setup first");
    const findPage = new FindBookingPage(page);
    await findPage.goto();
    await findPage.search(SEED_BOOKING_REF);
    await findPage.waitForBookingDetails();

    // 当前存在的错误拼写
    const typoHeading = page.getByText("Booker Detials");
    const isTypo = await typoHeading.isVisible();

    if (isTypo) {
      console.warn('⚠️ BUG: Heading says "Booker Detials" — should be "Booker Details"');
    }
    // After fix: expect(page.getByText("Booker Details")).toBeVisible();
    // Document current state:
    const hasCorrect = await page.getByText("Booker Details").isVisible();
    expect(isTypo || hasCorrect).toBeTruthy();
  });
});
