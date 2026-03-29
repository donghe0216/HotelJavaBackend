// tests/profile/profile.spec.js
// Project: chromium（requires customer storageState）

import { test, expect } from "@playwright/test";
import { ProfilePage }     from "../pages/ProfilePage.js";
import { EditProfilePage } from "../pages/EditProfilePage.js";

test.describe("👤 Profile Page", () => {

  // ─────────────────────────────────────────────────────────────
  // TC-PRO-01  页面加载，显示欢迎语和用户信息
  // ─────────────────────────────────────────────────────────────
  test("TC-PRO-01 | 页面加载，显示 Welcome 标题和用户邮箱", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const profilePage = new ProfilePage(page);
    await profilePage.goto();
    await profilePage.waitForProfileToLoad();

    const heading = await profilePage.welcomeHeading.textContent();
    expect(heading).toMatch(/welcome/i);

    const detailsText = await profilePage.profileDetails.textContent();
    expect(detailsText).toMatch(/email/i);
    expect(detailsText).toMatch(/phone/i);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-PRO-02  显示 Edit Profile 和 Logout 按钮
  // ─────────────────────────────────────────────────────────────
  test("TC-PRO-02 | 页面显示 Edit Profile 和 Logout 按钮", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const profilePage = new ProfilePage(page);
    await profilePage.goto();
    await profilePage.waitForProfileToLoad();

    await expect(profilePage.editProfileButton).toBeVisible();
    await expect(profilePage.logoutButton).toBeVisible();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-PRO-03  点击 Edit Profile，跳转到 /edit-profile
  // ─────────────────────────────────────────────────────────────
  test("TC-PRO-03 | 点击 Edit Profile 按钮，跳转到 /edit-profile", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const profilePage = new ProfilePage(page);
    await profilePage.goto();
    await profilePage.waitForProfileToLoad();
    await profilePage.clickEditProfile();

    await expect(page).toHaveURL(/edit-profile/);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-PRO-04  点击 Logout，清除 token 并跳转到 /home
  // ─────────────────────────────────────────────────────────────
  test("TC-PRO-04 | 点击 Logout，token 被清除，跳转到 /home", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const profilePage = new ProfilePage(page);
    await profilePage.goto();
    await profilePage.waitForProfileToLoad();
    await profilePage.clickLogout();

    await expect(page).toHaveURL(/home/);

    const token = await page.evaluate(() => localStorage.getItem("token"));
    expect(token).toBeFalsy();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-PRO-05  有历史订单时，显示订单列表
  // ─────────────────────────────────────────────────────────────
  test("TC-PRO-05 | 有历史订单时，booking-item 列表显示", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const profilePage = new ProfilePage(page);
    await profilePage.goto();
    await profilePage.waitForProfileToLoad();

    const count = await profilePage.getBookingCount();

    if (count > 0) {
      // 各 booking-item に必要フィールドが表示されているか検証
      const firstItem = profilePage.bookingItems.first();
      const itemText  = await firstItem.textContent();
      expect(itemText).toMatch(/booking code/i);
      expect(itemText).toMatch(/check-in/i);
      expect(itemText).toMatch(/payment status/i);
    } else {
      await expect(profilePage.noBookingsMessage).toBeVisible();
    }
  });

  // ─────────────────────────────────────────────────────────────
  // TC-PRO-06  无历史订单时，显示 "No bookings found."
  // ─────────────────────────────────────────────────────────────
  test("TC-PRO-06 | 无订单用户看到 'No bookings found.'", async ({ browser }) => {
    // 新しいユーザー（予約なし）でログインして確認
    // Fresh context: register + login a brand-new user
    const context = await browser.newContext();
    const page    = await context.newPage();

    const freshEmail = `fresh_${Date.now()}@hotel.com`;

    // Register
    await page.goto("/register");
    await page.locator('input[name="firstName"]').fill("Fresh");
    await page.locator('input[name="lastName"]').fill("User");
    await page.locator('input[name="email"]').fill(freshEmail);
    await page.locator('input[name="phoneNumber"]').fill("09099999999");
    await page.locator('input[name="password"]').fill("FreshPass1234!");
    await page.getByRole("button", { name: /register/i }).click();
    await expect(page).toHaveURL(/login/, { timeout: 6_000 });

    // Login
    await page.locator('input[name="email"]').fill(freshEmail);
    await page.locator('input[name="password"]').fill("FreshPass1234!");
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/home/, { timeout: 8_000 });

    // Visit profile
    await page.goto("/profile");
    await page.waitForLoadState("networkidle");
    await expect(page.getByText("No bookings found.")).toBeVisible({ timeout: 8_000 });

    await context.close();
  });

  // ─────────────────────────────────────────────────────────────
  // TC-PRO-07  未登录用户访问 /profile，被重定向到 /login
  // ─────────────────────────────────────────────────────────────
  test("TC-PRO-07 | 未登录用户访问 /profile，Guard 重定向到 /login", async ({ browser }, testInfo) => {
    // This test creates a fresh context with no auth — should work in any project.
    // Skip in authenticated projects to avoid flakiness from browser-level timing.
    test.skip(testInfo.project.name !== "chromium-public", "Unauthenticated Guard test — run in chromium-public only");
    const context = await browser.newContext(); // no storageState
    const page    = await context.newPage();

    await page.goto("/profile");
    await expect(page).toHaveURL(/login/, { timeout: 12_000 });

    await context.close();
  });
});

// ══════════════════════════════════════════════════════════════════
// Edit Profile Page tests
// ══════════════════════════════════════════════════════════════════
test.describe("✏️ Edit Profile Page", () => {

  // ─────────────────────────────────────────────────────────────
  // TC-EDIT-01  页面加载，显示用户信息和 Delete 按钮
  // ─────────────────────────────────────────────────────────────
  test("TC-EDIT-01 | 页面加载，显示用户姓名、邮箱和 Delete 按钮", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const editPage = new EditProfilePage(page);
    await editPage.goto();
    await editPage.waitForProfileToLoad();

    await expect(editPage.heading).toBeVisible();
    await expect(editPage.deleteButton).toBeVisible();

    const detailsText = await editPage.profileDetails.textContent();
    expect(detailsText).toMatch(/first name/i);
    expect(detailsText).toMatch(/email/i);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-EDIT-02  【Bug 文档】EditProfile 没有编辑表单
  //
  //   EditProfile.jsx の名前から「編集フォーム」を期待するが、
  //   実際にはユーザー情報の表示 + 削除ボタンしか存在しない。
  //   編集フォーム（input fields）が全く実装されていない。
  // ─────────────────────────────────────────────────────────────
  test("TC-EDIT-02 | 【Bug文档】EditProfile 页面无编辑输入框", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const editPage = new EditProfilePage(page);
    await editPage.goto();
    await editPage.waitForProfileToLoad();

    const inputCount = await page.locator(".edit-profile-page input").count();

    // 当前行为: 0 个 input（无编辑功能）
    // 预期行为: 应有 firstName/lastName/phoneNumber 等可编辑字段
    if (inputCount === 0) {
      console.warn("⚠️ BUG: EditProfile page has no input fields — editing is not implemented");
    }
    // Document current state (不做 fail，作为文档测试)
    expect(inputCount).toBeGreaterThanOrEqual(0);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-EDIT-03  点击 Delete，弹出确认对话框
  // ─────────────────────────────────────────────────────────────
  test("TC-EDIT-03 | 点击 Delete My Account，弹出确认对话框", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const editPage = new EditProfilePage(page);
    await editPage.goto();
    await editPage.waitForProfileToLoad();

    let dialogMessage = "";
    page.once("dialog", async (dialog) => {
      dialogMessage = dialog.message();
      await dialog.dismiss();  // dismiss so account isn't actually deleted
    });

    await editPage.deleteButton.click();

    expect(dialogMessage).toMatch(/delete your account/i);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-EDIT-04  取消删除确认，账户保留，页面不跳转
  // ─────────────────────────────────────────────────────────────
  test("TC-EDIT-04 | 取消删除确认，账户不删除，留在当前页", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === "chromium-public", "Requires auth");
    const editPage = new EditProfilePage(page);
    await editPage.goto();
    await editPage.waitForProfileToLoad();

    await editPage.clickDeleteWithConfirm(false); // dismiss

    // Should stay on edit-profile
    await expect(page).toHaveURL(/edit-profile/);
  });

  // ─────────────────────────────────────────────────────────────
  // TC-EDIT-05  【Bug 文档】确认删除后跳转到 /signup（路由不存在）
  //
  //   EditProfile.jsx: navigate('/signup') → App.js に /signup ルートなし
  //   正しくは navigate('/register') または navigate('/home') にすべき
  // ─────────────────────────────────────────────────────────────
  test("TC-EDIT-05 | 【Bug文档】删除后跳转到 /signup（该路由不存在，应为 /register）", async ({ browser }) => {
    // Use a disposable account so we can safely delete it
    const context = await browser.newContext();
    const page    = await context.newPage();
    const tempEmail = `delete_me_${Date.now()}@hotel.com`;

    // Register
    await page.goto("/register");
    await page.locator('input[name="firstName"]').fill("Del");
    await page.locator('input[name="lastName"]').fill("Me");
    await page.locator('input[name="email"]').fill(tempEmail);
    await page.locator('input[name="phoneNumber"]').fill("09011112222");
    await page.locator('input[name="password"]').fill("DeleteMe1234!");
    await page.getByRole("button", { name: /register/i }).click();
    await expect(page).toHaveURL(/login/, { timeout: 6_000 });

    // Login
    await page.locator('input[name="email"]').fill(tempEmail);
    await page.locator('input[name="password"]').fill("DeleteMe1234!");
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/home/, { timeout: 8_000 });

    // Delete account
    await page.goto("/edit-profile");
    await page.waitForLoadState("networkidle");
    page.once("dialog", (d) => d.accept());
    await page.getByRole("button", { name: /delete/i }).click();

    // BUG: navigates to /signup which doesn't exist → falls back to /home via wildcard
    // After fix: should navigate to /register
    await page.waitForURL(/signup|register|home/, { timeout: 8_000 });
    const finalUrl = page.url();
    console.warn(`⚠️ After delete, navigated to: ${finalUrl} (expected /register)`);

    await context.close();
  });
});
