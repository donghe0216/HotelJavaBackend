# Hotel Booking System — Backend

[![API Tests](https://github.com/donghe0216/HotelJavaBackend/actions/workflows/api-tests.yml/badge.svg)](https://github.com/donghe0216/HotelJavaBackend/actions/workflows/api-tests.yml)

Spring Boot REST API for a hotel booking system, built as a QA portfolio project demonstrating two-layer test architecture, state machine testing, and security boundary validation. **61 unit tests / 123 API tests** across 12 test classes.

**Frontend repo:** https://github.com/donghe0216/HotelReactFrontend  
**Live:** https://d1sr0fmxk50vjd.cloudfront.net/home

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.4.1, Java 21 |
| Auth | JWT (stateless) |
| Database | MySQL 8 + Spring Data JPA |
| Unit Tests | JUnit 5, Mockito |
| API Tests | REST Assured |
| Coverage | JaCoCo |
| CI/CD | GitHub Actions |
| Hosting | AWS EC2 + RDS |

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | Public | Register new user |
| POST | `/api/auth/login` | Public | Login, returns JWT |
| GET | `/api/rooms/all` | Public | List all rooms |
| GET | `/api/rooms/{id}` | Public | Room detail |
| POST | `/api/rooms/add` | ADMIN | Add room |
| PUT | `/api/rooms/update/{id}` | ADMIN | Update room |
| DELETE | `/api/rooms/delete/{id}` | ADMIN | Delete room |
| POST | `/api/bookings` | CUSTOMER | Create booking |
| GET | `/api/bookings/all` | ADMIN | List all bookings |
| GET | `/api/bookings/{ref}` | Any auth | Find by reference |
| PUT | `/api/bookings/update` | ADMIN | Update booking status |
| POST | `/api/bookings/{id}/cancel` | CUSTOMER / ADMIN | Cancel booking |
| GET | `/api/users/all` | ADMIN | List all users |
| GET | `/api/users/profile` | Any auth | Current user profile |
| DELETE | `/api/users/delete` | Any auth | Delete own account |

---

## Test Architecture

Two layers, one JUnit 5 runner:

| Package | Type | Dependencies |
|---------|------|-------------|
| `unit/` | Mockito unit tests | No Spring context, no DB |
| `api/` | REST Assured integration tests | Live server on `:9090` + MySQL |

**Decision rule:**
- Business logic (date validation, price calculation, state machine) → **unit tests**
- HTTP status codes, DB persistence, security boundaries (401/403) → **API tests**

### Test Files

| File | Test Cases | Scope |
|------|-----------|-------|
| `unit/BookingServiceImplTest` | TC-BS-01–18, TC-BS-CANCEL-01–03 | Booking business logic |
| `unit/RoomServiceImplTest` | TC-RS-01–29 | Room business logic |
| `unit/UserServiceImplTest` | TC-US-01–10 | User business logic |
| `unit/BookingCodeGeneratorTest` | TC-BCG-01–04 | Booking reference generation |
| `api/BookingApiTest` | TC-B-01–18 | Booking CRUD endpoints |
| `api/BookingStateMachineApiTest` | TC-BSM-01–09 | Status transition validation |
| `api/AuthorizationTest` | TC-AUTH-01–25 | 401/403 security boundary tests |
| `api/RoomApiTest` | TC-R-01–27 | Room management endpoints |
| `api/UserApiTest` | TC-U-01–26 | User management endpoints |
| `api/ConcurrentBookingTest` | TC-CON-01–02 | Concurrent booking conflict |
| `api/IdempotencyTest` | TC-IDEM-01–05 | Duplicate request handling |
| `api/StateConsistencyTest` | TC-SC-01–05 | Cross-operation state consistency |

### Booking State Machine

```
BOOKED → CHECKED_IN   ✅
BOOKED → CANCELLED    ✅
BOOKED → NO_SHOW      ✅
CHECKED_IN → CHECKED_OUT ✅
All other transitions  ❌ → 400
```

---

## Bug Report

### Fixed

Bugs found during development and testing, now resolved.

| ID | Location | Description | Severity | Found By |
|----|----------|-------------|----------|---------|
| BUG-B-01 | `BookingController` | `POST /bookings/{id}/cancel` endpoint was never implemented — frontend cancel button silently failed with 404 | High | E2E TC-PRO-08 |
| BUG-B-02 | `RoomServiceImpl` | Room deletion silently failed with a generic 409 FK constraint error when historical bookings existed — even CANCELLED bookings blocked deletion | Medium | Manual testing |
| BUG-B-03 | `RoomServiceImpl` | `addRoom` input validation (null / range checks) was lost during monorepo migration — invalid rooms could be persisted | Medium | CI failure (10 unit tests) |
| BUG-B-04 | `BookingServiceImpl` | Status transition errors returned raw enum strings (`"Invalid status transition: CHECKED_OUT → BOOKED"`) instead of business-language messages | Low | Manual testing |
| BUG-B-05 | `BookingServiceImpl` | `NullPointerException` in `calculateTotalPrice` when `pricePerNight` is null | Medium | Unit test |

### Known (Preserved)

Intentionally kept as portfolio material. Each demonstrates a different class of defect.

| ID | Location | Description | Severity | Found By |
|----|----------|-------------|----------|---------|
| BUG-K-01 | `BookingServiceImpl` | Date validation compares `checkInDate` against itself instead of `checkOutDate` — zero-night bookings (`checkIn == checkOut`) are accepted | Medium | Unit test TC-B-05 |
| BUG-K-02 | `SecurityConfig` | `PUT /bookings/update` has no auth guard — anonymous users can modify any booking status | High | API test TC-AUTH-12 |
| BUG-K-03 | `SecurityConfig` | `GET /bookings/{ref}` is publicly accessible — anonymous users can retrieve full booking details including personal data | Critical | API test TC-AUTH-24 |
| BUG-K-04 | `SecurityConfig` | IDOR: authenticated users can retrieve any other user's booking by reference — no ownership check | High | API test TC-AUTH-25 |
| BUG-K-05 | `BookingCodeGenerator` | Check-then-act race condition — under high concurrency, a duplicate reference code triggers `DataIntegrityViolationException` instead of retrying | Medium | Code review |

---

<details>
<summary>Local Setup</summary>

**Prerequisites:**
- Java 21+
- Maven 3.8+
- MySQL 8

```bash
# 1. Clone the repository
git clone https://github.com/donghe0216/HotelJavaBackend.git
cd HotelJavaBackend

# 2. Create the database
mysql -u root -p -e "CREATE DATABASE hotel;"

# 3. Create application-local.properties (gitignored)
#    src/main/resources/application-local.properties
spring.datasource.password=yourpassword
secreteJwtString=your-jwt-secret

# 4. Start the server (Hibernate auto-creates schema on first run)
./mvnw spring-boot:run

# 5. Seed test data (required before running API tests)
curl -s -X POST http://localhost:9090/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Customer","lastName":"User","email":"customer@hotel.com","password":"Customer1234!","phoneNumber":"09012345678"}'

curl -s -X POST http://localhost:9090/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Admin","lastName":"User","email":"admin@hotel.com","password":"Admin1234!","phoneNumber":"09012345678"}'

# Promote admin role
mysql -u root -p hotel -e "UPDATE users SET role='ADMIN' WHERE email='admin@hotel.com';"

# Insert a seed room
mysql -u root -p hotel -e "
  INSERT INTO rooms (room_number, type, price_per_night, capacity, description, image_url)
  VALUES (101, 'DOUBLE', 150.00, 2, 'Standard double room', '/images/placeholder.jpg');
"
```

### Run Tests

```bash
# All tests (requires live server on :9090)
./mvnw test

# Unit tests only (no server needed)
./mvnw test -Dtest="unit.*"

# API tests only
./mvnw test -Dtest="api.*"

# Verbose REST Assured logging
./mvnw test -Dtest.verbose=true
```

</details>

---

## Infrastructure

Provisioned with Terraform on AWS:

| Service | Purpose |
|---------|---------|
| EC2 | Spring Boot application server |
| RDS MySQL | Database (private subnet) |
| S3 | Frontend static files + deployment artifacts |
| CloudFront | CDN for frontend |
| Secrets Manager | DB credentials, JWT secret |
| IAM OIDC | Keyless GitHub Actions authentication |
