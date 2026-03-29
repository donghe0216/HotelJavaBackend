// tests/pages/LoginPage.js
// Page Object for /login
//
// LoginPage.jsx の構造から逆算:
//   - email input    : name="email"
//   - password input : name="password"
//   - submit button  : type="submit", text="Login"
//   - error message  : className="error-message"
//   - register link  : href="/register"

export class LoginPage {
  constructor(page) {
    this.page = page;

    // ── Locators ──────────────────────────────────────────────────
    this.emailInput    = page.locator('input[name="email"]');
    this.passwordInput = page.locator('input[name="password"]');
    this.submitButton  = page.getByRole("button", { name: /login/i });
    this.errorMessage  = page.locator(".error-message");
    this.registerLink  = page.locator('a[href="/register"]');
  }

  // ── Actions ───────────────────────────────────────────────────

  async goto() {
    await this.page.goto("/login");
  }

  async login(email, password) {
    await this.emailInput.fill(email);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }

  async getErrorMessage() {
    await this.errorMessage.waitFor({ state: "visible", timeout: 6_000 });
    return this.errorMessage.textContent();
  }
}
