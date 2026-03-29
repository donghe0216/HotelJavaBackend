// tests/pages/ProfilePage.js
// ProfilePage.jsx の構造から逆算:
//   - Welcome heading   : h2 "Welcome, {firstName}"
//   - Edit Profile btn  : button.edit-profile-button
//   - Logout btn        : button.logout-button
//   - profile details   : .profile-details  (email, phone)
//   - booking list      : .booking-list  > .booking-item[]
//   - no bookings msg   : "No bookings found."
//   - error message     : .error-message

export class ProfilePage {
  constructor(page) {
    this.page = page;

    this.welcomeHeading    = page.locator(".profile-page h2");
    this.editProfileButton = page.locator("button.edit-profile-button");
    this.logoutButton      = page.locator("button.logout-button");
    this.profileDetails    = page.locator(".profile-details");
    this.bookingItems      = page.locator(".booking-item");
    this.noBookingsMessage = page.getByText("No bookings found.");
    this.errorMessage      = page.locator(".error-message");
  }

  async goto() {
    await this.page.goto("/profile");
    await this.page.waitForLoadState("networkidle");
  }

  async waitForProfileToLoad() {
    await this.welcomeHeading.waitFor({ state: "visible", timeout: 10_000 });
  }

  async getBookingCount() {
    return this.bookingItems.count();
  }

  async clickEditProfile() {
    await this.editProfileButton.click();
  }

  async clickLogout() {
    await this.logoutButton.click();
  }
}
