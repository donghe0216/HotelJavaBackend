// tests/pages/AdminPage.js

export class AdminPage {
  constructor(page) {
    this.page = page;

    this.welcomeHeading      = page.locator(".welcome-message");
    this.manageRoomsButton   = page.getByRole("button", { name: /manage rooms/i });
    this.manageBookingsButton = page.getByRole("button", { name: /manage bookings/i });
  }

  async goto() {
    await this.page.goto("/admin");
    await this.page.waitForLoadState("networkidle");
  }

  async waitForLoad() {
    await this.welcomeHeading.waitFor({ state: "visible", timeout: 10_000 });
  }
}

// ─────────────────────────────────────────────────────────────────
// ManageRoomPage
// MangeRoomPage.jsx の構造:
//   - h2 "All Rooms"
//   - filter select + Add Room button
//   - RoomResult cards  (.room-listing)
//   - Pagination
// ─────────────────────────────────────────────────────────────────
export class ManageRoomPage {
  constructor(page) {
    this.page = page;

    this.heading        = page.getByRole("heading", { name: /all rooms/i });
    this.roomTypeSelect = page.locator(".filter-select-div select");
    this.addRoomButton  = page.locator("button.add-room-button");
    this.roomCards      = page.locator(".room-listing");   // adjust to RoomResult card class
  }

  async goto() {
    await this.page.goto("/admin/manage-rooms");
    await this.page.waitForLoadState("networkidle");
  }

  async waitForLoad() {
    await this.heading.waitFor({ state: "visible", timeout: 10_000 });
  }

  async getRoomCount() {
    return this.roomCards.count();
  }

  async selectRoomType(type) {
    await this.roomTypeSelect.selectOption(type);
    await this.page.waitForTimeout(300);
  }

  async clickAddRoom() {
    await this.addRoomButton.click();
  }

  /** Click the edit/manage button on the nth room card (0-indexed) */
  async clickEditRoom(index = 0) {
    await this.roomCards.nth(index)
      .getByRole("button", { name: /edit|manage/i })
      .click();
  }
}

// ─────────────────────────────────────────────────────────────────
// ManageBookingsPage
// ManageBookingPage.jsx の構造:
//   - h2 "All Bookings"
//   - search input  placeholder="Enter booking number"
//   - .booking-result-item[]  each with "Manage Booking" button
//   - Pagination
// ─────────────────────────────────────────────────────────────────
export class ManageBookingsPage {
  constructor(page) {
    this.page = page;

    this.heading        = page.getByRole("heading", { name: /all bookings/i });
    this.searchInput    = page.getByPlaceholder(/enter booking number/i);
    this.bookingItems   = page.locator(".booking-result-item");
  }

  async goto() {
    await this.page.goto("/admin/manage-bookings");
    await this.page.waitForLoadState("networkidle");
  }

  async waitForLoad() {
    await this.heading.waitFor({ state: "visible", timeout: 10_000 });
  }

  async getBookingCount() {
    return this.bookingItems.count();
  }

  async search(term) {
    await this.searchInput.fill(term);
    await this.page.waitForTimeout(300); // useMemo re-renders synchronously
  }

  async clearSearch() {
    await this.searchInput.clear();
    await this.page.waitForTimeout(300);
  }

  /** Click "Manage Booking" on the nth item (0-indexed) */
  async clickManageBooking(index = 0) {
    await this.bookingItems.nth(index)
      .getByRole("button", { name: /manage booking/i })
      .click();
  }
}
