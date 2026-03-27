# 🛒 E-Commerce Platform — Spring Boot Backend

A production-ready REST API for an e-commerce platform built with **Spring Boot 3**, **Spring Security + JWT**, **Spring Data JPA**, and **Stripe** payment processing.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     REST API  (:8080)                       │
│                                                             │
│  AuthController  ProductController  CartController          │
│  OrderController  PaymentController                         │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    Service Layer                             │
│                                                             │
│  AuthService  ProductService  CartService                   │
│  OrderService  PaymentService (Stripe)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│               Spring Data JPA Repositories                  │
│                                                             │
│  User  Product  Cart/CartItem  Order/OrderItem              │
│  RefreshToken                                               │
└──────────────────────────┬──────────────────────────────────┘
                           │
              ┌────────────▼────────────┐
              │   PostgreSQL Database   │
              └─────────────────────────┘
```

### Entity Relationships

| Relationship                  | Type        |
|-------------------------------|-------------|
| User → Orders                 | One-to-Many |
| User → Cart                   | One-to-One  |
| Cart → CartItems              | One-to-Many |
| CartItem → Product            | Many-to-One |
| Order → OrderItems            | One-to-Many |
| OrderItem → Product           | Many-to-One |

---

## Tech Stack

| Concern            | Technology                          |
|--------------------|-------------------------------------|
| Framework          | Spring Boot 3.2                     |
| Security           | Spring Security 6 + JWT (jjwt 0.12) |
| Persistence        | Spring Data JPA + Hibernate         |
| Database           | PostgreSQL 16 (H2 for dev/test)     |
| Payments           | Stripe Java SDK 24                  |
| Build              | Maven 3.9                           |
| Runtime            | Java 17                             |
| Containerisation   | Docker + Docker Compose             |

---

## Quick Start

### 1 — Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose (for PostgreSQL)
- A [Stripe](https://stripe.com) account (test mode keys are fine)

### 2 — Configure Environment
```bash
cp .env.example .env
# Edit .env and fill in STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET
```

### 3 — Start Database
```bash
docker-compose up postgres -d
```

### 4 — Run the Application
```bash
# With PostgreSQL
mvn spring-boot:run

# With embedded H2 (no DB required)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5 — Run All Tests
```bash
mvn test
```

### 6 — Full Docker Stack
```bash
docker-compose up --build
```

---

## API Reference

> All protected endpoints require `Authorization: Bearer <access_token>`.

### Authentication — `/api/auth`

| Method | Path              | Auth | Description               |
|--------|-------------------|------|---------------------------|
| POST   | `/register`       | ✗    | Register a new user        |
| POST   | `/login`          | ✗    | Login, receive JWT pair    |
| POST   | `/refresh`        | ✗    | Rotate refresh token       |
| POST   | `/logout`         | ✗    | Invalidate refresh token   |

**Register request body:**
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "securePass1",
  "firstName": "Alice",
  "lastName": "Smith"
}
```

**Login request body:**
```json
{ "usernameOrEmail": "alice@example.com", "password": "securePass1" }
```

**Auth response:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid-v4-token",
  "tokenType": "Bearer",
  "userId": 1,
  "username": "alice",
  "email": "alice@example.com",
  "role": "ROLE_USER"
}
```

---

### Products — `/api/products`

| Method | Path                  | Auth       | Description              |
|--------|-----------------------|------------|--------------------------|
| GET    | `/`                   | ✗          | Paginated product list   |
| GET    | `/{id}`               | ✗          | Single product           |
| GET    | `/search?q=...`       | ✗          | Full-text search         |
| GET    | `/category/{cat}`     | ✗          | Filter by category       |
| GET    | `/price-range?min&max`| ✗          | Filter by price range    |
| GET    | `/categories`         | ✗          | All available categories |
| POST   | `/`                   | ADMIN      | Create product           |
| PUT    | `/{id}`               | ADMIN      | Update product           |
| DELETE | `/{id}`               | ADMIN      | Soft-delete product      |

**Pagination params:** `page` (default 0), `size` (default 20), `sortBy` (default `createdAt`), `direction` (`asc`/`desc`).

**Create/update body:**
```json
{
  "name": "Wireless Mouse",
  "description": "Ergonomic 3-button mouse",
  "price": 34.99,
  "stockQuantity": 200,
  "imageUrl": "https://cdn.example.com/mouse.png",
  "category": "Electronics"
}
```

---

### Cart — `/api/cart`  *(requires auth)*

| Method | Path              | Description                   |
|--------|-------------------|-------------------------------|
| GET    | `/`               | View current user's cart      |
| POST   | `/items`          | Add item to cart              |
| PUT    | `/items/{productId}?quantity=N` | Update item quantity |
| DELETE | `/items/{productId}` | Remove item from cart      |
| DELETE | `/`               | Clear entire cart             |

**Add-item body:**
```json
{ "productId": 5, "quantity": 2 }
```

---

### Orders — `/api/orders`  *(requires auth)*

| Method | Path                        | Auth  | Description              |
|--------|-----------------------------|-------|--------------------------|
| POST   | `/`                         | USER  | Place order from cart    |
| GET    | `/`                         | USER  | List my orders           |
| GET    | `/{orderId}`                | USER  | Get order details        |
| PATCH  | `/{orderId}/cancel`         | USER  | Cancel pending order     |
| GET    | `/admin/all`                | ADMIN | List all orders          |
| PATCH  | `/admin/{orderId}/status`   | ADMIN | Update order status      |

**Place-order body:**
```json
{
  "addressLine1": "123 Main Street",
  "addressLine2": "Apt 4B",
  "city": "New York",
  "state": "NY",
  "zip": "10001",
  "country": "US"
}
```

**Order response (on creation includes `stripeClientSecret`):**
```json
{
  "orderId": 42,
  "totalPrice": 69.98,
  "status": "PENDING",
  "paymentStatus": "PENDING",
  "stripeClientSecret": "pi_xxx_secret_yyy",
  "items": [ ... ],
  "shippingDetails": { ... }
}
```

---

### Payments — `/api/payments`

| Method | Path       | Auth | Description              |
|--------|------------|------|--------------------------|
| POST   | `/webhook` | ✗    | Stripe webhook receiver  |

Register `POST /api/payments/webhook` in your Stripe Dashboard.

---

## Payment Flow

```
Client                    API Server               Stripe
  │                           │                      │
  ├──POST /orders────────────►│                      │
  │                           ├──CreatePaymentIntent►│
  │                           │◄─clientSecret────────┤
  │◄──{stripeClientSecret}────┤                      │
  │                           │                      │
  ├──stripe.confirmPayment───────────────────────────►
  │                           │                      │
  │                           │◄──webhook: succeeded─┤
  │                           │  (status → CONFIRMED)│
```

1. Client places an order → API creates a Stripe `PaymentIntent` and returns `clientSecret`.
2. Client uses Stripe.js / mobile SDK to confirm payment with the `clientSecret`.
3. Stripe calls the `/api/payments/webhook` endpoint.
4. Server verifies the signature and updates `paymentStatus` and `orderStatus`.

---

## Security

- **JWT Access Token** — 24-hour expiry, signed with HS256.
- **Refresh Token** — 7-day expiry, stored in DB, rotated on each use (one active session per user).
- **Role-based access** — `ROLE_USER` and `ROLE_ADMIN` enforced via `@PreAuthorize`.
- **Cart isolation** — Users can only access their own cart (enforced via `userId` from JWT principal).
- **Order isolation** — Users can only view/cancel their own orders.
- **Stripe webhook** — Signature verified using `STRIPE_WEBHOOK_SECRET` before any state mutation.
- **Passwords** — BCrypt with strength factor 12.

---

## Error Responses

All errors follow the same envelope:

```json
{
  "success": false,
  "message": "Product not found with id: '99'",
  "timestamp": "2025-01-15T10:30:00"
}
```

| HTTP Status | Scenario                          |
|-------------|-----------------------------------|
| 400         | Validation failure / bad input    |
| 401         | Missing / invalid JWT             |
| 403         | Insufficient role                 |
| 404         | Resource not found                |
| 402         | Payment processing error          |
| 500         | Unexpected server error           |

---

## Project Structure

```
src/
├── main/java/com/ecommerce/
│   ├── config/          # SecurityConfig, StripeConfig
│   ├── controller/      # REST controllers
│   ├── dto/
│   │   ├── request/     # Inbound payloads (validated)
│   │   └── response/    # Outbound JSON shapes
│   ├── entity/          # JPA entities
│   ├── exception/       # Custom exceptions + GlobalExceptionHandler
│   ├── repository/      # Spring Data repositories
│   ├── security/        # JWT filter, UserDetailsImpl, JwtUtils
│   └── service/
│       └── impl/        # Service implementations
└── test/java/com/ecommerce/
    ├── controller/      # MockMvc integration tests
    ├── security/        # JWT unit tests
    └── service/         # Service unit tests (Mockito)
```
