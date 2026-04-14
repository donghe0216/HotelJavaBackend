# ホテル予約システム — バックエンド

[![API Tests](https://github.com/donghe0216/HotelJavaBackend/actions/workflows/api-tests.yml/badge.svg)](https://github.com/donghe0216/HotelJavaBackend/actions/workflows/api-tests.yml)

QAポートフォリオ用のSpring Boot REST APIです。  
ユニットテスト・APIテスト・E2Eテストの**3層テストアーキテクチャ**を採用し、バックエンドだけで**ユニットテスト 61件 / APIテスト 123件**を実装しています。状態遷移テスト・セキュリティ境界テスト・並列処理テストを含みます。

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
- ビジネスロジック（日付バリデーション・料金計算・状態遷移）→ **ユニットテスト**
- HTTPステータスコード・DB永続化・セキュリティ境界（401/403）→ **APIテスト**
- ユーザー視点の業務フロー → **E2Eテスト**

> **設計のポイント：** ユニットテストでSpringコンテキストを起動しない、APIテストでビジネスロジックをモックしない、という原則を徹底しています。テストの責務を明確に分離することで、実行速度と保守性を両立しています。

### テストファイル一覧

| ファイル | テストケース | スコープ |
|--------|------------|---------|
| `unit/BookingServiceImplTest` | TC-BS-01–18、TC-BS-CANCEL-01–03 | 予約ビジネスロジック |
| `unit/RoomServiceImplTest` | TC-RS-01–29 | 部屋管理ビジネスロジック |
| `unit/UserServiceImplTest` | TC-US-01–10 | ユーザー管理ビジネスロジック |
| `unit/BookingCodeGeneratorTest` | TC-BCG-01–04 | 予約番号生成 |
| `api/BookingApiTest` | TC-B-01–18 | 予約CRUDエンドポイント |
| `api/BookingStateMachineApiTest` | TC-BSM-01–09 | ステータス遷移バリデーション |
| `api/AuthorizationTest` | TC-AUTH-01–25 | 401/403セキュリティ境界 |
| `api/RoomApiTest` | TC-R-01–27 | 部屋管理エンドポイント |
| `api/UserApiTest` | TC-U-01–26 | ユーザー管理エンドポイント |
| `api/ConcurrentBookingTest` | TC-CON-01–02 | 同一部屋への同時予約競合 |
| `api/IdempotencyTest` | TC-IDEM-01–05 | 重複リクエスト副作用なし検証 |
| `api/StateConsistencyTest` | TC-SC-01–05 | 操作間の状態整合性検証 |

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

## バグレポート

### 修正済み

開発・テスト中に発見し、修正したバグです。

| ID | 場所 | 内容 | 深刻度 | 発見方法 |
|----|------|------|--------|---------|
| BUG-B-01 | `BookingController` | `POST /bookings/{id}/cancel` エンドポイントが未実装 — フロントエンドのキャンセルボタンが404を返しサイレントに失敗 | High | E2E TC-PRO-08 |
| BUG-B-02 | `RoomServiceImpl` | 過去のキャンセル済み予約が存在するだけで部屋削除がFK制約エラー（409）でサイレントに失敗 | Medium | 手動テスト |
| BUG-B-03 | `RoomServiceImpl` | モノレポ移行時にaddRoomのnull・範囲チェックが消失 — 無効な部屋データが保存可能に | Medium | CI失敗（ユニットテスト10件） |
| BUG-B-04 | `BookingServiceImpl` | ステータス遷移エラーが `"Invalid status transition: CHECKED_OUT → BOOKED"` という生のenum文字列を返していた | Low | 手動テスト |
| BUG-B-05 | `BookingServiceImpl` | `pricePerNight` がnullの場合に `calculateTotalPrice` で `NullPointerException` | Medium | ユニットテスト |

### 既知（保留中）

ポートフォリオ素材として意図的に残しているバグです。それぞれ異なる種類のセキュリティ・ロジック欠陥を示しています。

| ID | 場所 | 内容 | 深刻度 | 発見方法 |
|----|------|------|--------|---------|
| BUG-K-01 | `BookingServiceImpl` | 日付バリデーションが `checkOutDate` の代わりに `checkInDate` と自身を比較 — 0泊予約（`checkIn == checkOut`）が通過し `totalPrice = 0` になる | Medium | ユニットテスト TC-B-05 |
| BUG-K-02 | `SecurityConfig` | `PUT /bookings/update` に認証ガードなし — 未認証ユーザーが任意の予約ステータスを変更可能 | High | APIテスト TC-AUTH-12 |
| BUG-K-03 | `SecurityConfig` | `GET /bookings/{ref}` が公開アクセス可能 — 未認証ユーザーが個人情報を含む予約詳細を取得可能 | Critical | APIテスト TC-AUTH-24 |
| BUG-K-04 | `SecurityConfig` | IDOR：認証済みユーザーが他ユーザーの予約を参照可能 — 所有者チェックなし | High | APIテスト TC-AUTH-25 |
| BUG-K-05 | `BookingCodeGenerator` | Check-then-act競合状態 — 高負荷時に重複予約コードで `DataIntegrityViolationException` が発生しリトライされない | Medium | コードレビュー |

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
