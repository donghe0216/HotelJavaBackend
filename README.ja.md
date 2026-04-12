# ホテル予約システム — バックエンド

[![API Tests](https://github.com/donghe0216/HotelJavaBackend/actions/workflows/api-tests.yml/badge.svg)](https://github.com/donghe0216/HotelJavaBackend/actions/workflows/api-tests.yml)

QAポートフォリオ用のSpring Boot REST APIです。  
ユニットテスト・APIテスト・E2Eテストの**3層テストアーキテクチャ**を採用し、バックエンドだけで**ユニットテスト 65件 / APIテスト 124件**を実装しています。ステートマシンテスト・セキュリティ境界テスト・並列処理テストを含みます。

**ライブデモ：** https://d1sr0fmxk50vjd.cloudfront.net/home  
**フロントエンド（Playwright E2Eテスト）：** https://github.com/donghe0216/HotelReactFrontend  
---

## 技術スタック

| レイヤー | 技術 |
|---------|------|
| フレームワーク | Spring Boot 3.4.1、Java 21 |
| 認証 | JWT（ステートレス） |
| データベース | MySQL 8 + Spring Data JPA |
| ユニットテスト | JUnit 5、Mockito |
| APIテスト | REST Assured |
| カバレッジ | JaCoCo |
| CI/CD | GitHub Actions |
| ホスティング | AWS EC2 + RDS |

---

## テストアーキテクチャ

### 3層テスト設計

| 層 | パッケージ | 種類 | 依存関係 |
|----|-----------|------|---------|
| Layer 1 | `unit/` | Mockitoユニットテスト | Springコンテキストなし・DBなし |
| Layer 2 | `api/` | REST Assured統合テスト | 稼働中サーバー（`:9090`）+ MySQL |
| Layer 3 | フロントエンドrepo | Playwright E2Eテスト | ブラウザ + フルスタック |

**層の判断基準：**
- ビジネスロジック（日付バリデーション・料金計算・ステートマシン）→ **ユニットテスト**
- HTTPステータスコード・DB永続化・セキュリティ境界（401/403）→ **APIテスト**
- ユーザー視点の業務フロー → **E2Eテスト**

> **設計のポイント：** ユニットテストでSpringコンテキストを起動しない、APIテストでビジネスロジックをモックしない、という原則を徹底しています。テストの責務を明確に分離することで、実行速度と保守性を両立しています。

### テストファイル一覧

| ファイル | テストケース | スコープ |
|--------|------------|---------|
| `unit/BookingServiceImplTest` | TC-BS-01〜18、TC-BS-CANCEL-01〜03 | 予約ビジネスロジック |
| `unit/RoomServiceImplTest` | TC-RS-* | 部屋管理ビジネスロジック |
| `unit/UserServiceImplTest` | TC-US-* | ユーザー管理ビジネスロジック |
| `api/BookingApiTest` | TC-B-01〜18 | 予約CRUDエンドポイント |
| `api/BookingStateMachineApiTest` | TC-BSM-01〜09 | ステータス遷移バリデーション |
| `api/AuthorizationTest` | TC-AUTH-* | 401/403セキュリティ境界 |
| `api/RoomApiTest` | TC-R-* | 部屋管理エンドポイント |
| `api/UserApiTest` | TC-U-* | ユーザー管理エンドポイント |
| `api/ConcurrentBookingTest` | TC-CON-* | 同一部屋への同時予約競合（排他制御） |
| `api/IdempotencyTest` | TC-IDEM-* | 重複リクエストによる副作用なし検証 |
| `api/StateConsistencyTest` | TC-SC-* | 操作間の状態伝播・整合性検証 |

---

## 予約ステートマシン

```
BOOKED     → CHECKED_IN   ✅
BOOKED     → CANCELLED    ✅
BOOKED     → NO_SHOW      ✅
CHECKED_IN → CHECKED_OUT  ✅
上記以外の遷移              ❌ → 400
```

不正な遷移はすべてAPIテストでカバーしています。

---

## ドキュメント化されたバグ

バグはポートフォリオの素材として意図的に残しています。

**TC-B-05 — 日付バリデーション自己比較バグ**

| 項目 | 内容 |
|------|------|
| 場所 | `BookingServiceImpl.createBooking()` |
| 原因 | `checkOutDate.isBefore(checkInDate)` の条件式で、`checkOutDate` の代わりに `checkInDate` と自身を比較している |
| リスク | `checkIn == checkOut`（0泊）の予約が通過し、`totalPrice = 0` のデータが生成される |
| テスト戦略 | ユニットテストで境界値（`checkIn == checkOut`、`checkIn > checkOut`）を直接検証。HTTPレイヤーは関係しないためAPIテストには含めない |
| 修正後の検証 | 条件式を修正後、同じユニットテストが `InvalidBookingStateAndDateException` を検出することを確認 |

**TC-AUTH-PAY — 未認証エンドポイント**

| 項目 | 内容 |
|------|------|
| 場所 | `PUT /payments/update` |
| リスク | 認証ガードなし — 未認証ユーザーが支払いステータスを任意に変更可能 |
| テスト戦略 | `AuthorizationTest` で未認証リクエスト → 401、非ADMINロール → 403 を検証 |

> **設計のポイント：** バグを発見するだけでなく、「なぜ起きるか」「どの層でテストすべきか」「修正後にどう検証するか」まで説明できる状態にしています。

---

## APIエンドポイント

| メソッド | パス | 認証 | 説明 |
|--------|------|------|------|
| POST | `/api/auth/register` | 公開 | ユーザー登録 |
| POST | `/api/auth/login` | 公開 | ログイン・JWT取得 |
| GET | `/api/rooms/all` | 公開 | 部屋一覧 |
| GET | `/api/rooms/{id}` | 公開 | 部屋詳細 |
| POST | `/api/rooms/add` | ADMIN | 部屋追加 |
| PUT | `/api/rooms/update/{id}` | ADMIN | 部屋更新 |
| DELETE | `/api/rooms/delete/{id}` | ADMIN | 部屋削除 |
| POST | `/api/bookings` | CUSTOMER | 予約作成 |
| GET | `/api/bookings/all` | ADMIN | 予約一覧 |
| GET | `/api/bookings/{ref}` | 認証済み | 予約番号で照会 |
| PUT | `/api/bookings/update` | ADMIN | 予約ステータス更新 |
| POST | `/api/bookings/{id}/cancel` | CUSTOMER / ADMIN | 予約キャンセル |
| GET | `/api/users/all` | ADMIN | ユーザー一覧 |
| GET | `/api/users/profile` | 認証済み | 自分のプロフィール |
| DELETE | `/api/users/delete` | 認証済み | アカウント削除 |

---

<details>
<summary>ローカル環境セットアップ</summary>

**前提条件：**
- Java 21以上
- Maven 3.8以上
- MySQL 8

```bash
# 1. リポジトリをクローン
git clone https://github.com/donghe0216/HotelJavaBackend.git
cd HotelJavaBackend

# 2. データベースを作成
mysql -u root -p -e "CREATE DATABASE hotel;"

# 3. application-local.properties を作成（gitignore済み）
#    src/main/resources/application-local.properties
spring.datasource.password=yourpassword
secreteJwtString=your-jwt-secret

# 4. 起動（初回起動時にHibernateがスキーマを自動生成）
./mvnw spring-boot:run

# 5. シードデータを投入（APIテスト実行前に必要）
curl -s -X POST http://localhost:9090/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Customer","lastName":"User","email":"customer@hotel.com","password":"Customer1234!","phoneNumber":"09012345678"}'

curl -s -X POST http://localhost:9090/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Admin","lastName":"User","email":"admin@hotel.com","password":"Admin1234!","phoneNumber":"09012345678"}'

# adminロールに昇格
mysql -u root -p hotel -e "UPDATE users SET role='ADMIN' WHERE email='admin@hotel.com';"

# 部屋を1件追加
mysql -u root -p hotel -e "
  INSERT INTO rooms (room_number, type, price_per_night, capacity, description, image_url)
  VALUES (101, 'DOUBLE', 150.00, 2, 'Standard double room', '/images/placeholder.jpg');
"
```

### テストの実行

```bash
# 全テスト（稼働中サーバーが必要）
./mvnw test

# ユニットテストのみ（サーバー不要）
./mvnw test -Dtest="unit.*"

# APIテストのみ
./mvnw test -Dtest="api.*"

# 詳細ログあり
./mvnw test -Dtest.verbose=true
```

</details>

### 必要なシードアカウント

```
customer@hotel.com / Customer1234!
admin@hotel.com    / Admin1234!
```

---

## インフラ構成

Terraformを使用してAWS上にプロビジョニング：

| サービス | 用途 |
|---------|------|
| EC2 | Spring Bootアプリケーションサーバー |
| RDS MySQL | データベース（プライベートサブネット） |
| S3 | フロントエンド静的ファイル・デプロイアーティファクト |
| CloudFront | フロントエンドCDN |
| Secrets Manager | DB認証情報・JWTシークレット |
| IAM OIDC | キーレスGitHub Actions認証 |
