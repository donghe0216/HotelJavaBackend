// tests/pages/RegisterPage.js
// Register.jsx の構造から逆算:
//   - fields: firstName, lastName, email, phoneNumber, password
//   - input type: email フィールドのみ "email"、それ以外 "text"
//   - submit button: text="Register"
//   - success message: className="success-message"
//   - error message  : className="error-message"
//   - login link     : href="/login"

export class RegisterPage {
  constructor(page) {
    this.page = page;

    // ── Locators ──────────────────────────────────────────────────
    this.firstNameInput   = page.locator('input[name="firstName"]');
    this.lastNameInput    = page.locator('input[name="lastName"]');
    this.emailInput       = page.locator('input[name="email"]');
    this.phoneNumberInput = page.locator('input[name="phoneNumber"]');
    this.passwordInput    = page.locator('input[name="password"]');
    this.submitButton     = page.getByRole("button", { name: /register/i });
    this.successMessage   = page.locator(".success-message");
    this.errorMessage     = page.locator(".error-message");
    this.loginLink        = page.locator('a[href="/login"]');
  }

  async goto() {
    await this.page.goto("/register");
  }

  async fillForm({ firstName, lastName, email, phoneNumber, password }) {
    if (firstName   !== undefined) await this.firstNameInput.fill(firstName);
    if (lastName    !== undefined) await this.lastNameInput.fill(lastName);
    if (email       !== undefined) await this.emailInput.fill(email);
    if (phoneNumber !== undefined) await this.phoneNumberInput.fill(phoneNumber);
    if (password    !== undefined) await this.passwordInput.fill(password);
  }

  async register(data) {
    await this.fillForm(data);
    await this.submitButton.click();
  }

  async getSuccessMessage() {
    await this.successMessage.waitFor({ state: "visible", timeout: 6_000 });
    return this.successMessage.textContent();
  }

  async getErrorMessage() {
    await this.errorMessage.waitFor({ state: "visible", timeout: 6_000 });
    return this.errorMessage.textContent();
  }
}
