// tests/pages/FindBookingPage.js
// FindBookingPage.jsx の構造から逆算:
//   - input            : placeholder="Enter your booking confirmation code"
//   - Find button      : button text="Find"
//   - booking details  : .booking-details
//   - error message    : style="color: red" (inline style — no className)
//   - booker details section
//   - room details section

export class FindBookingPage {
  constructor(page) {
    this.page = page;

    this.codeInput      = page.getByPlaceholder(/booking confirmation code/i);
    this.findButton     = page.getByRole("button", { name: /find/i });

    // error uses inline style="color:red", no className
    this.errorMessage   = page.locator("p[style*='color: red'], p[style*='color:red']");

    this.bookingDetails = page.locator(".booking-details");
    this.bookingCode    = page.locator(".booking-details p").nth(0);
    this.checkInDate    = page.locator(".booking-details p").nth(1);
    this.checkOutDate   = page.locator(".booking-details p").nth(2);
    this.paymentStatus  = page.locator(".booking-details p").nth(3);
  }

  async goto() {
    await this.page.goto("/find-booking");
  }

  async search(code) {
    await this.codeInput.fill(code);
    await this.findButton.click();
  }

  async getErrorMessage() {
    await this.errorMessage.waitFor({ state: "visible", timeout: 6_000 });
    return this.errorMessage.textContent();
  }

  async waitForBookingDetails() {
    await this.bookingDetails.waitFor({ state: "visible", timeout: 10_000 });
  }
}
