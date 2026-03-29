// tests/pages/EditProfilePage.js
// EditProfile.jsx の構造から逆算:
//   - h2               : "Edit Profile"
//   - profile details  : .profile-details（読み取り専用表示）
//   - delete button    : button.delete-profile-button
//   - confirm dialog   : window.confirm (ブラウザネイティブ)
//   - error message    : .error-message
//
// ⚠️ 注意: EditProfile.jsx は実際には「編集フォーム」ではなく
//   ユーザー情報の表示 + アカウント削除機能のみ。
//   名前が EditProfile だが編集フォームは存在しない。

export class EditProfilePage {
  constructor(page) {
    this.page = page;

    this.heading         = page.getByRole("heading", { name: /edit profile/i });
    this.profileDetails  = page.locator(".profile-details");
    // getByRole はテキストベースで安定。CSSクラス名はリファクタリングで壊れやすい。
    this.deleteButton    = page.getByRole("button", { name: /delete/i });
    this.errorMessage    = page.locator(".error-message");
  }

  async goto() {
    await this.page.goto("/edit-profile");
    await this.page.waitForLoadState("networkidle");
  }

  async waitForProfileToLoad() {
    await this.profileDetails.waitFor({ state: "visible", timeout: 10_000 });
  }

  /** 削除確認ダイアログを accept または dismiss する */
  async clickDeleteWithConfirm(accept = true) {
    this.page.once("dialog", (dialog) => {
      if (accept) dialog.accept();
      else dialog.dismiss();
    });
    await this.deleteButton.click();
  }
}
