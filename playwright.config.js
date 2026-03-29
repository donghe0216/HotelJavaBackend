// playwright.config.js
// 放置路径: /hotel-react-frontend/playwright.config.js

import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",

  // 每个测试最长运行时间
  timeout: 30_000,

  // 断言最长等待时间
  expect: { timeout: 5_000 },

  // 失败时自动重试（CI 环境建议 2，本地建议 0）
  retries: 0,

  // 并行 worker 数
  workers: process.env.CI ? 1 : 2,

  reporter: [
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["list"],
  ],

  use: {
    // 前端开发服务器地址
    baseURL: "http://localhost:3000",

    // 失败时保留截图和 trace
    screenshot: "only-on-failure",
    trace: "on-first-retry",
    video: "on-first-retry",

    // 浏览器语言设置
    locale: "en-US",
  },

  projects: [
    // ── 认证准备（所有需要登录的测试依赖此 setup）─────────────────
    {
      name: "setup",
      testMatch: /.*\.setup\.js/,
    },

    // ── Chrome（主要测试环境）────────────────────────────────────
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // 已登录的 customer 状态（由 auth.setup.js 生成）
        storageState: "tests/.auth/customer.json",
      },
      dependencies: ["setup"],
    },

    // ── 不需要登录的页面（首页/登录页/注册页/房间列表）──────────
    {
      name: "chromium-public",
      use: { ...devices["Desktop Chrome"] },
    },

    // ── Admin 专属路由测试 ────────────────────────────────────────
    {
      name: "chromium-admin",
      use: {
        ...devices["Desktop Chrome"],
        storageState: "tests/.auth/admin.json",
      },
      dependencies: ["setup"],
    },
  ],

  // ── 前端服务器启动策略 ──────────────────────────────────────────
  //
  //   CI   (process.env.CI=true):
  //     1. npm run build  → 生成 production bundle (build/)
  //     2. npx serve -s build -l 3000  → 静态伺服，端口 3000
  //     优点：测试的是真实 production 产物（minified、tree-shaken），
  //           同时验证 build 本身不会报错。
  //
  //   Local (CI 未设置):
  //     npm start  → CRA webpack dev server，端口 3000，支持 HMR
  //     reuseExistingServer=true：本地已经在跑的话不会重复启动。
  //
  //   Playwright 通过轮询 url 等待服务 ready（最长 timeout 毫秒），
  //   所有测试结束后自动 kill 这个进程。
  webServer: {
    command: process.env.CI
      ? "CI=false npm run build && npx serve -s build -l 3000"
      : "npm start",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000, // CI: build ~60s + serve startup; local: ~10s
  },
});
