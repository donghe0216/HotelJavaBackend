// tests/auth.setup.js
// 放置路径: /hotel-react-frontend/tests/auth.setup.js
//
// 作用：
//   在所有测试运行之前，分别以 customer 和 admin 身份登录，
//   把浏览器的 localStorage（JWT token + role）保存到文件。
//   后续测试直接加载该状态，不需要每次重新走登录流程。

import { test as setup, expect } from "@playwright/test";
import fs from "fs";
import path from "path";

// ── 测试账号（与后端种子数据保持一致）──────────────────────────
const CUSTOMER = { email: "customer@hotel.com", password: "Customer1234!" };
const ADMIN    = { email: "admin@hotel.com",    password: "Admin1234!" };

const API_BASE = "http://localhost:9090/api";

// ── 存储状态文件路径 ─────────────────────────────────────────────
const AUTH_DIR       = path.join("tests", ".auth");
const CUSTOMER_FILE  = path.join(AUTH_DIR, "customer.json");
const ADMIN_FILE     = path.join(AUTH_DIR, "admin.json");
const BOOKING_FILE   = path.join(AUTH_DIR, "booking.json");

// 确保目录存在
if (!fs.existsSync(AUTH_DIR)) fs.mkdirSync(AUTH_DIR, { recursive: true });

// ── 通用登录函数 ─────────────────────────────────────────────────
async function loginAs(page, { email, password }) {
  await page.goto("/login");

  await page.locator('input[name="email"]').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole("button", { name: /login/i }).click();

  // 登录成功后会跳转到 /home
  await expect(page).toHaveURL(/home/, { timeout: 10_000 });
}

// ── Setup 1: 保存 Customer 登录状态 ──────────────────────────────
setup("authenticate as customer", async ({ page }) => {
  await loginAs(page, CUSTOMER);
  await page.context().storageState({ path: CUSTOMER_FILE });
});

// ── Setup 2: 保存 Admin 登录状态 ─────────────────────────────────
setup("authenticate as admin", async ({ page }) => {
  await loginAs(page, ADMIN);
  await page.context().storageState({ path: ADMIN_FILE });
});

// ── Setup 3: 创建 find-booking 测试所需的 seed 预订 ─────────────
//   使用 Playwright request API（不启动浏览器），创建一条真实预订，
//   将 bookingReference 写入文件供 find-booking.spec.js 读取。
//   失败时静默忽略——TC-FB-06~09 会以 test.skip 跳过而不报错。
setup("create seed booking", async ({ request }) => {
  // 1. Login via API to get JWT
  const loginRes = await request.post(`${API_BASE}/auth/login`, {
    data: { email: CUSTOMER.email, password: CUSTOMER.password },
  });
  if (!loginRes.ok()) return;
  const { token } = await loginRes.json();

  // 2. Get the first available room
  const roomsRes = await request.get(`${API_BASE}/rooms/all`);
  if (!roomsRes.ok()) return;
  const { rooms } = await roomsRes.json();
  const roomId = rooms?.[0]?.id;
  if (!roomId) return;

  // 3. Create a booking far in the future to avoid date conflicts
  const checkIn  = new Date();
  checkIn.setFullYear(checkIn.getFullYear() + 2);
  const fmt = (d) => d.toISOString().slice(0, 10);
  const checkOut = new Date(checkIn);
  checkOut.setDate(checkOut.getDate() + 2);

  const bookingRes = await request.post(`${API_BASE}/bookings/create`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { roomId, checkInDate: fmt(checkIn), checkOutDate: fmt(checkOut) },
  });
  if (!bookingRes.ok()) return;
  const { booking } = await bookingRes.json();
  if (!booking?.bookingReference) return;

  fs.writeFileSync(BOOKING_FILE, JSON.stringify({ ref: booking.bookingReference }));
});
