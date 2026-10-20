# CampusBite — Complete API Documentation

This document provides a comprehensive, authoritative technical specification for all application programming interfaces (APIs), form action handlers, and administrative endpoints implemented in the **CampusBite** Campus Canteen Ordering System.

> [!NOTE]
> **Source of Truth:** This specification is derived directly from the application source code (`com.campus.canteen.*`), database schema (`database/campusbite.sql`), and runtime configuration (`canteen/src/main/resources/application.properties`).

---

## Table of Contents

- [1. Base URL, Protocols & Conventions](#1-base-url-protocols--conventions)
  - [1.1 Architectural Design Principles](#11-architectural-design-principles)
- [2. Endpoint Inventory Table](#2-endpoint-inventory-table)
- [3. Authentication Mechanisms](#3-authentication-mechanisms)
  - [3.1 Web Session Authentication (HttpSession + Token Sync)](#31-web-session-authentication-httpsession--token-sync)
  - [3.2 Stateless API Opaque Tokens (API v2)](#32-stateless-api-opaque-tokens-api-v2)
  - [3.3 Administrator Cookie Authentication (ADMINSESSIONID)](#33-administrator-cookie-authentication-adminsessionid)
  - [3.4 Password Reset Tokens (SMTP Recovery)](#34-password-reset-tokens-smtp-recovery)
- [4. User Authentication Endpoints](#4-user-authentication-endpoints)
  - [4.1 POST /auth/login (Web Sign-In)](#41-post-authlogin-web-sign-in)
  - [4.2 GET /auth/logout (Web Sign-Out)](#42-get-authlogout-web-sign-out)
  - [4.3 GET /auth/refreshSession (Web Session Sync)](#43-get-authrefreshsession-web-session-sync)
  - [4.4 POST /auth/forgot-password (Password Recovery Request)](#44-post-authforgot-password-password-recovery-request)
  - [4.5 POST /auth/reset-password (Password Reset Submission)](#45-post-authreset-password-password-reset-submission)
  - [4.6 POST /api/v1/auth/register (API v1 Registration)](#46-post-apiv1authregister-api-v1-registration)
  - [4.7 POST /api/v1/auth/signin (API v1 Session Sign-In)](#47-post-apiv1authsignin-api-v1-session-sign-in)
  - [4.8 POST /api/v1/auth/logout (API v1 Session Sign-Out)](#48-post-apiv1authlogout-api-v1-session-sign-out)
  - [4.9 GET /api/v1/auth/session (API v1 Session Check)](#49-get-apiv1authsession-api-v1-session-check)
  - [4.10 POST /api/v2/auth/login (API v2 Bearer Token Login)](#410-post-apiv2authlogin-api-v2-bearer-token-login)
  - [4.11 POST /api/v2/auth/validate (API v2 Token Validation)](#411-post-apiv2authvalidate-api-v2-token-validation)
  - [4.12 POST /api/v2/auth/logout (API v2 Token Revocation)](#412-post-apiv2authlogout-api-v2-token-revocation)
- [5. User Profile Endpoints](#5-user-profile-endpoints)
  - [5.1 POST /profile/change/name (Web Profile Update Name)](#51-post-profilechangename-web-profile-update-name)
  - [5.2 POST /profile/change/roll-no (Web Profile Update Roll Number)](#52-post-profilechangeroll-no-web-profile-update-roll-number)
  - [5.3 POST /profile/change-password (Web Profile Password Change)](#53-post-profilechange-password-web-profile-password-change)
  - [5.4 POST /api/v2/profile/details (API v2 Profile Retrieval)](#54-post-apiv2profiledetails-api-v2-profile-retrieval)
  - [5.5 POST /api/v2/profile/change/name (API v2 Update Name)](#55-post-apiv2profilechangename-api-v2-update-name)
  - [5.6 POST /api/v2/profile/change/roll_no (API v2 Update Roll Number)](#56-post-apiv2profilechangeroll_no-api-v2-update-roll-number)
- [6. Menu and Administrator Endpoints](#6-menu-and-administrator-endpoints)
  - [6.1 POST /admin/api/login (Administrator Login)](#61-post-adminapilogin-administrator-login)
  - [6.2 POST /admin/api/logout (Administrator Logout)](#62-post-adminapilogout-administrator-logout)
  - [6.3 GET /admin/api/items (Admin List Menu Items)](#63-get-adminapiitems-admin-list-menu-items)
  - [6.4 POST /admin/api/items (Admin Create Menu Item)](#64-post-adminapiitems-admin-create-menu-item)
  - [6.5 PUT /admin/api/items/{oldId} (Admin Update Menu Item)](#65-put-adminapiitemsoldid-admin-update-menu-item)
  - [6.6 DELETE /admin/api/items/{id} (Admin Delete Menu Item)](#66-delete-adminapiitemsid-admin-delete-menu-item)
  - [6.7 POST /admin/orders/{orderId}/status (Admin Order Status Update)](#67-post-adminordersorderidstatus-admin-order-status-update)
- [7. Order and Checkout Endpoints](#7-order-and-checkout-endpoints)
  - [7.1 POST /order/checkout (Cart Submission & Order Placement)](#71-post-ordercheckout-cart-submission--order-placement)
- [8. Payment Endpoints](#8-payment-endpoints)
  - [8.1 POST /payment/bill (Generate Payment Bill)](#81-post-paymentbill-generate-payment-bill)
  - [8.2 POST /payment/pay (Simulate Payment Execution)](#82-post-paymentpay-simulate-payment-execution)
  - [8.3 POST /order/confirmation (Order Confirmation Receipt)](#83-post-orderconfirmation-order-confirmation-receipt)
- [9. Other Implemented Endpoints](#9-other-implemented-endpoints)
  - [9.1 GET /api/v1/ (System Health & Version)](#91-get-apiv1-system-health--version)
- [10. End-to-End Request Workflows](#10-end-to-end-request-workflows)
  - [Workflow 1: User Registration, Sign-In & Profile Updates](#workflow-1-user-registration-sign-in--profile-updates)
  - [Workflow 2: Menu Browsing, Authoritative Checkout & Payment Simulation](#workflow-2-menu-browsing-authoritative-checkout--payment-simulation)
  - [Workflow 3: Administrator Menu Management & Order Progression](#workflow-3-administrator-menu-management--order-progression)
  - [Workflow 4: Self-Service Password Recovery via Email Token](#workflow-4-self-service-password-recovery-via-email-token)
- [11. Error Handling and Status Codes](#11-error-handling-and-status-codes)

---

## 1. Base URL, Protocols & Conventions

- **Default Base URL:** `http://localhost:8080` (configured via `app.password-reset.base-url=${APP_BASE_URL:http://localhost:8080}`)
- **Transport Protocols:** HTTP/1.1
- **Default Media Formats:**
  - JSON REST Requests & Responses: `Content-Type: application/json;charset=UTF-8`
  - HTML Form Submissions: `Content-Type: application/x-www-form-urlencoded`
  - Server-Rendered Views: `Content-Type: text/html;charset=UTF-8`
- **CSRF Policy:** Spring Security explicitly disables CSRF (`csrf(csrf -> csrf.disable())` in `SecurityConfig.java`). No CSRF tokens are required for API or form POST requests.
- **Character Encoding:** UTF-8 throughout.
- **Monetary Currency:** Indian Rupee (INR / ₹) formatted to 2 decimal places (`DECIMAL(10,2)` in MySQL).

### 1.1 Architectural Design Principles

The API design incorporates several deliberate engineering choices:

1. **Dual-Channel Authentication Strategy:**
   - **Web Application Channel (`/auth/*`, `/profile/*`):** Backed by Servlet `HttpSession` paired with continuous database token validation (`SessionRefreshInterceptor`). This facilitates stateful browser navigation while ensuring server-side revocation takes immediate effect.
   - **Stateless Client Channel (`/api/v2/*`):** Backed by `Authorization: Bearer <TOKEN>` headers delivering 32-byte opaque tokens. Purpose-built for mobile applications and external systems without cookie dependencies.
2. **Context-Appropriate URL Conventions:**
   - Browser form endpoints follow web URL conventions in kebab-case (e.g., `/profile/change/roll-no`).
   - REST v2 microservice endpoints follow standard JSON/API snake_case conventions (e.g., `/api/v2/profile/change/roll_no`).
3. **Decoupled Payment Gateway Simulation:**
   - Payment execution (`POST /payment/pay`) and confirmation (`POST /order/confirmation`) deliberately operate independently of the student's web session. This models real-world payment gateway callbacks and webhooks (such as Razorpay or Stripe) where external payment servers trigger state changes out-of-band. Security is guaranteed by high-entropy, cryptographically random transaction identifiers (`CB-` followed by 32 bytes, providing 256 bits of entropy).
4. **Isolated Administrator Domain:**
   - Canteen staff administration uses an independent in-memory session store (`ConcurrentHashMap`) and a dedicated `ADMINSESSIONID` cookie with `SameSite=Strict` and constant-time credential comparisons. This guarantees complete privilege isolation: student session states cannot interact with or compromise administrative controls.

---

## 2. Endpoint Inventory Table

| Method | URL | Purpose | Authentication |
| :--- | :--- | :--- | :--- |
| **User Authentication** | | | |
| `POST` | `/auth/login` | Student web login; establishes session and issues API token | None (Public) |
| `GET` | `/auth/logout` | Revokes API token and invalidates student web session | Website `HttpSession` |
| `GET` | `/auth/refreshSession` | Refreshes session attributes against latest DB state | Website `HttpSession` |
| `POST` | `/auth/forgot-password` | Dispatches password recovery link via SMTP email | None (Public) |
| `POST` | `/auth/reset-password` | Resets password using email recovery token | None (Token payload) |
| `POST` | `/api/v1/auth/register` | Registers new student account (V1 REST) | None (Public) |
| `POST` | `/api/v1/auth/signin` | Student sign-in establishing cookie session (V1 REST) | None (Public) |
| `POST` | `/api/v1/auth/logout` | Invalidates cookie session (V1 REST) | Website `HttpSession` |
| `GET` | `/api/v1/auth/session` | Inspects current user session authentication state | Website `HttpSession` |
| `POST` | `/api/v2/auth/login` | Generates 5-day opaque Bearer token (V2 REST) | None (Public) |
| `POST` | `/api/v2/auth/validate` | Validates active status of an opaque Bearer token | Opaque Bearer Token |
| `POST` | `/api/v2/auth/logout` | Revokes an active opaque Bearer token | Opaque Bearer Token |
| **User Profile** | | | |
| `POST` | `/profile/change/name` | Updates student full name in session and database | Website `HttpSession` |
| `POST` | `/profile/change/roll-no` | Updates student roll number (enforces unique constraint) | Website `HttpSession` |
| `POST` | `/profile/change-password` | Validates current password and sets new BCrypt hash | Website `HttpSession` |
| `POST` | `/api/v2/profile/details` | Retrieves student profile details (V2 REST) | Opaque Bearer Token |
| `POST` | `/api/v2/profile/change/name` | Updates student full name (V2 REST) | Opaque Bearer Token |
| `POST` | `/api/v2/profile/change/roll_no` | Updates student roll number (V2 REST) | Opaque Bearer Token |
| **Menu & Administrator** | | | |
| `POST` | `/admin/api/login` | Authenticates admin and issues `ADMINSESSIONID` cookie | None (Public) |
| `POST` | `/admin/api/logout` | Clears admin session and invalidates cookie | Administrator Cookie |
| `GET` | `/admin/api/items` | Lists all menu items (available and unavailable) | Administrator Cookie |
| `POST` | `/admin/api/items` | Creates a new menu item in database | Administrator Cookie |
| `PUT` | `/admin/api/items/{oldId}` | Updates menu item fields, including primary key ID | Administrator Cookie |
| `DELETE` | `/admin/api/items/{id}` | Permanently deletes a menu item | Administrator Cookie |
| `POST` | `/admin/orders/{orderId}/status` | Advances status of a paid order | Administrator Cookie |
| **Orders & Checkout** | | | |
| `POST` | `/order/checkout` | Validates submitted cart items, computes authoritative bill | Website `HttpSession` |
| **Payments** | | | |
| `POST` | `/payment/bill` | Generates unique transaction ID and pending payment bill | Website `HttpSession` |
| `POST` | `/payment/pay` | Simulates payment execution, setting status to `PAID` | None (Transaction ID) |
| `POST` | `/order/confirmation` | Confirms paid order and renders final receipt | None (Transaction ID) |
| **Other Implemented APIs** | | | |
| `GET` | `/api/v1/` | Service health and API version status check | None (Public) |

---

## 3. Authentication Mechanisms

The application deliberately implements four independent security and credential mechanisms. None of these tokens use JSON Web Tokens (JWT).

```mermaid
flowchart TD
    subgraph AuthSchemes["CampusBite Authentication Schemes"]
        A["1. Web Session + Token Sync<br>(JSESSIONID Cookie + DB Token Check)"]
        B["2. API v2 Opaque Bearer Token<br>(Authorization: Bearer [32-byte Base64])"]
        C["3. Administrator Cookie<br>(ADMINSESSIONID HttpOnly Cookie)"]
        D["4. SMTP Reset Token<br>(32-byte Base64 URL Query Parameter)"]
    end
```

### 3.1 Web Session Authentication (HttpSession + Token Sync)
- **Scope:** Web interface routes (`/order/checkout`, `/payment/bill`, `/profile/*`, `/orders`).
- **Mechanism:** Standard Java Servlet `HttpSession` backed by browser cookie `JSESSIONID`.
- **Session Attributes:** `userId` (`Long`), `userEmail` (`String`), `fullName` (`String`), `rollNumber` (`String`), `apiToken` (`String`).
- **Continuous Synchronization:** The `SessionRefreshInterceptor` intercepts requests to `/`, `/menu`, `/cart`, and `/profile`. It extracts `apiToken` from the session, queries the `api_tokens` database table, and immediately invalidates the session if the token has been revoked, expired, or tampered with.

### 3.2 Stateless API Opaque Tokens (API v2)
- **Scope:** REST endpoints under `/api/v2/*`.
- **Token Generation:** A 32-byte cryptographically secure random sequence generated via `java.security.SecureRandom`, encoded as URL-safe Base64 without padding.
- **Storage & Hashing:** Raw tokens are delivered once to the client upon login. Only the **SHA-256 hash** (`CHAR(64)`) is stored in the `api_tokens` table.
- **Header Format:** `Authorization: Bearer <RAW_OPAQUE_TOKEN>`
- **Validity:** 5 days (`TOKEN_VALIDITY_DAYS = 5`) from creation timestamp.
- **Revocation:** Explicit logout (`POST /api/v2/auth/logout`) or a password change sets `revoked_at = CURRENT_TIMESTAMP`.

### 3.3 Administrator Cookie Authentication (ADMINSESSIONID)
- **Scope:** Administrator portal and endpoints (`/admin`, `/admin/api/*`, `/admin/orders/*`).
- **Isolation:** Completely isolated from student user sessions. Admin sessions are kept in an in-memory `ConcurrentHashMap<String, AdminSession>` inside `AdminController`.
- **Cookie Details:**
  - Name: `ADMINSESSIONID`
  - Attributes: `HttpOnly=true`, `SameSite=Strict`, `Path=/admin`, `Max-Age=28800` (8 hours).
  - Secure: Driven by `${app.admin.cookie-secure:false}`.
- **Credential Verification:** Credentials (`app.admin.username` and `app.admin.password`) are evaluated using constant-time byte comparisons via `MessageDigest.isEqual` to prevent timing attacks.

### 3.4 Password Reset Tokens (SMTP Recovery)
- **Scope:** Self-service password recovery (`/auth/forgot-password` and `/auth/reset-password`).
- **Token Specification:** 32-byte cryptographically random URL-safe Base64 token.
- **Storage & Hashing:** Stored as a SHA-256 hash in `password_reset_tokens`.
- **Validity:** 30 minutes from issuance.
- **Single-Use Enforcement:** Consumed tokens are stamped with `used_at = CURRENT_TIMESTAMP`. Resetting the password automatically invalidates all other active tokens and active API tokens for that user account.

---

## 4. User Authentication Endpoints

### 4.1 POST /auth/login (Web Sign-In)

- **Name:** Student Web Authentication
- **Purpose:** Verifies student credentials, generates an opaque token, establishes a fresh HTTP session, and provides a validated redirect target.
- **HTTP Method:** `POST`
- **URL:** `/auth/login`
- **Query Parameters:**
  - `returnUrl` *(optional)*: Destination URL upon success. Sanitized to prevent open redirect vulnerabilities (must begin with `/`, cannot start with `//`, and cannot contain backslashes or line breaks; defaults to `/`).
- **Authentication:** None (Public).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `email` | `string` | Yes | Valid email, non-blank | Student registered email address |
| `password` | `string` | Yes | Non-empty | Plaintext password |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid credentials | `{"message": "Login successful.", "redirectUrl": "/"}` | Session created; `JSESSIONID` cookie set |
| `400 Bad Request` | Missing fields | `{"message": "Email and password are required."}` | One or both fields were omitted/empty |
| `401 Unauthorized` | Invalid password | `{"message": "Invalid email or password."}` | Email not found or BCrypt comparison failed |
| `401 Unauthorized` | Inactive user record | `{"message": "User account not found."}` | User details lookup failed post-authentication |

#### Sample Request

```bash
curl -i -X POST "http://localhost:8080/auth/login?returnUrl=/menu" \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "praveen@college.edu",
    "password": "password123"
  }'
```

#### Sample Response

```http
HTTP/1.1 200 OK
Set-Cookie: JSESSIONID=D8F9A1B2C3D4E5F60718293A4B5C6D7E; Path=/; HttpOnly
Content-Type: application/json

{
  "message": "Login successful.",
  "redirectUrl": "/menu"
}
```

---

### 4.2 GET /auth/logout (Web Sign-Out)

- **Name:** Student Web Logout
- **Purpose:** Revokes the user's active API token in the database, destroys the server-side `HttpSession`, and redirects to the landing page.
- **HTTP Method:** `GET`
- **URL:** `/auth/logout`
- **Authentication:** Website `HttpSession` (via `JSESSIONID` cookie).
- **Headers:** None.

#### Request Body
*None (HTTP GET request).*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `302 Found` | Always | *(Empty body; redirects to `/`)* | Token revoked and session invalidated |

#### Sample Request

```bash
curl -i -X GET http://localhost:8080/auth/logout \
  -b cookies.txt
```

---

### 4.3 GET /auth/refreshSession (Web Session Sync)

- **Name:** Refresh Web Session Attributes
- **Purpose:** Synchronizes session attributes (`fullName`, `rollNumber`, `userEmail`) against current database values using the stored `apiToken`.
- **HTTP Method:** `GET`
- **URL:** `/auth/refreshSession`
- **Authentication:** Website `HttpSession` containing an active `apiToken`.
- **Headers:** None.

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid token and session | `{"updated": true, "message": "Session details refreshed successfully."}` | Session updated with fresh DB records |
| `401 Unauthorized` | Invalid or revoked token | `{"updated": false, "message": "Session or token is invalid or expired."}` | Session destroyed on the server |

#### Sample Request

```bash
curl -i -X GET http://localhost:8080/auth/refreshSession \
  -b cookies.txt
```

---

### 4.4 POST /auth/forgot-password (Password Recovery Request)

- **Name:** Request Password Reset Link
- **Purpose:** Issues a 30-minute password reset token and sends an email via JavaMailSender. Implements timing-safe behavior to prevent email enumeration.
- **HTTP Method:** `POST`
- **URL:** `/auth/forgot-password`
- **Authentication:** None (Public).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `email` | `string` | Yes | Non-blank | Target student account email |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Any non-blank email | `{"message": "If an account exists for that email, a password-reset link will be sent."}` | Generic success response regardless of account existence |
| `400 Bad Request` | Missing email | `{"message": "Please enter your email address."}` | Email field was blank or omitted |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "praveen@college.edu"}'
```

---

### 4.5 POST /auth/reset-password (Password Reset Submission)

- **Name:** Execute Password Reset
- **Purpose:** Consumes a valid recovery token, hashes the new password with BCrypt, updates the database, revokes active API tokens, and marks the token used.
- **HTTP Method:** `POST`
- **URL:** `/auth/reset-password`
- **Authentication:** None (Token authenticated).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `token` | `string` | Yes | Non-blank | Raw 32-byte Base64 URL-safe token received via email |
| `newPassword` | `string` | Yes | Minimum 8 characters | New plaintext account password |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid token & password | `{"message": "Password reset successfully.", "redirectUrl": "/auth/login"}` | Password updated, tokens revoked |
| `400 Bad Request` | Missing token | `{"message": "The reset token is missing. Request a new link."}` | Token omitted |
| `400 Bad Request` | Short password | `{"message": "Password must contain at least 8 characters."}` | Length &lt; 8 characters |
| `400 Bad Request` | Expired/used token | `{"message": "The reset link is invalid, expired, or has already been used. Request a new link."}` | Token not found, expired, or used |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "4aB7k9LmNpQrStUvWxYz0123456789-_abcdefghijk",
    "newPassword": "newSecurePassword2026"
  }'
```

---

### 4.6 POST /api/v1/auth/register (API v1 Registration)

- **Name:** Student Account Registration (V1 API)
- **Purpose:** Creates a new student record in `users` with a BCrypt password hash.
- **HTTP Method:** `POST`
- **URL:** `/api/v1/auth/register`
- **Query Parameters:** `returnUrl` *(optional)*
- **Authentication:** None (Public).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `fullName` | `string` | Yes | Non-blank, &le; 100 chars | Student legal name |
| `email` | `string` | Yes | Non-blank, &le; 255 chars | Unique college email |
| `rollNumber` | `string` | Yes | Non-blank, &le; 50 chars | Unique student roll number |
| `password` | `string` | Yes | &ge; 8 characters | Plaintext password |
| `confirmPassword` | `string` | Yes | Must match `password` | Password confirmation check |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `201 Created` | Successful creation | `{"message": "Account created successfully. Please sign in.", "redirectUrl": "/auth/login?returnUrl=%2F"}` | Record inserted into `users` |
| `400 Bad Request` | Missing field | `{"message": "Please fill in all required fields."}` | One or more fields empty |
| `400 Bad Request` | Mismatched password | `{"message": "Passwords do not match."}` | Passwords do not match |
| `400 Bad Request` | Password too short | `{"message": "Password must contain at least 8 characters."}` | Length &lt; 8 |
| `409 Conflict` | Duplicate email or roll | `{"message": "That email or roll number is already registered."}` | Unique constraint violation |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Praveen Kumar",
    "email": "praveen@college.edu",
    "rollNumber": "2025103621",
    "password": "password123",
    "confirmPassword": "password123"
  }'
```

---

### 4.7 POST /api/v1/auth/signin (API v1 Session Sign-In)

- **Name:** Student Session Login (V1 API)
- **Purpose:** Verifies student credentials and initializes an HTTP session without generating a V2 API token.
- **HTTP Method:** `POST`
- **URL:** `/api/v1/auth/signin`
- **Query Parameters:** `returnUrl` *(optional)*
- **Authentication:** None (Public).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `email` | `string` | Yes | Non-blank | Registered email |
| `password` | `string` | Yes | Non-blank | Plaintext password |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid credentials | `{"message": "Login successful.", "redirectUrl": "/"}` | HTTP session established |
| `400 Bad Request` | Missing fields | `{"message": "Email and password are required."}` | Missing credentials |
| `401 Unauthorized` | Invalid password | `{"message": "Invalid email or password."}` | Authentication failed |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/signin \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "praveen@college.edu",
    "password": "password123"
  }'
```

---

### 4.8 POST /api/v1/auth/logout (API v1 Session Sign-Out)

- **Name:** Invalidate Session (V1 API)
- **Purpose:** Invalidates the caller's `HttpSession`.
- **HTTP Method:** `POST`
- **URL:** `/api/v1/auth/logout`
- **Authentication:** Website `HttpSession`.
- **Headers:** None.

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Always | `{"message": "Logged out successfully.", "redirectUrl": "/"}` | Session invalidated |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/logout \
  -b cookies.txt
```

---

### 4.9 GET /api/v1/auth/session (API v1 Session Check)

- **Name:** Query Session Authentication State
- **Purpose:** Inspects the active `HttpSession` and returns authenticated user metadata.
- **HTTP Method:** `GET`
- **URL:** `/api/v1/auth/session`
- **Authentication:** Website `HttpSession`.
- **Headers:** None.

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Authenticated | `{"authenticated": true, "userId": 1, "userEmail": "praveen@college.edu", "fullName": "Praveen Kumar"}` | Active session details |
| `401 Unauthorized` | No active session | `{"authenticated": false}` | Unauthenticated caller |

#### Sample Request

```bash
curl -i -X GET http://localhost:8080/api/v1/auth/session \
  -b cookies.txt
```

---

### 4.10 POST /api/v2/auth/login (API v2 Bearer Token Login)

- **Name:** Issue Stateless API Token
- **Purpose:** Authenticates user credentials and issues a raw 32-byte opaque token valid for 5 days.
- **HTTP Method:** `POST`
- **URL:** `/api/v2/auth/login`
- **Authentication:** None (Public).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `email` | `string` | Yes | Non-blank | Registered email address |
| `password` | `string` | Yes | Non-empty | Plaintext password |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid credentials | `{"token": "vA7G_wS1-jK09LmNoPqRsTuVwXyZ1234567890abcdef"}` | Returns opaque Bearer token |
| `400 Bad Request` | Missing fields | `{"message": "Email and password are required."}` | Missing fields |
| `401 Unauthorized` | Bad credentials | `{"message": "Invalid email or password."}` | Authentication failed |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v2/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "praveen@college.edu",
    "password": "password123"
  }'
```

---

### 4.11 POST /api/v2/auth/validate (API v2 Token Validation)

- **Name:** Verify Bearer Token Status
- **Purpose:** Checks whether an opaque Bearer token exists, has not expired, and has not been revoked.
- **HTTP Method:** `POST`
- **URL:** `/api/v2/auth/validate`
- **Authentication:** Opaque Bearer Token (`Authorization: Bearer <TOKEN>`).
- **Headers:** `Authorization: Bearer <TOKEN>` *(optional header; missing header results in `{ "valid": false }`)*

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Token is active | `{"valid": true}` | Token valid and unexpired |
| `200 OK` | Token is invalid/expired/missing | `{"valid": false}` | Token cannot be used |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v2/auth/validate \
  -H "Authorization: Bearer vA7G_wS1-jK09LmNoPqRsTuVwXyZ1234567890abcdef"
```

---

### 4.12 POST /api/v2/auth/logout (API v2 Token Revocation)

- **Name:** Revoke Bearer Token
- **Purpose:** Sets `revoked_at = CURRENT_TIMESTAMP` for the specified token in `api_tokens`.
- **HTTP Method:** `POST`
- **URL:** `/api/v2/auth/logout`
- **Authentication:** Opaque Bearer Token.
- **Headers:** `Authorization: Bearer <TOKEN>`

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Successfully revoked | `{"success": true}` | Token is now revoked |
| `400 Bad Request` | Missing header/token | `{"success": false}` | Header omitted or malformed |
| `401 Unauthorized` | Token already revoked/not found | `{"success": false}` | No active token row was updated |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v2/auth/logout \
  -H "Authorization: Bearer vA7G_wS1-jK09LmNoPqRsTuVwXyZ1234567890abcdef"
```

---

## 5. User Profile Endpoints

### 5.1 POST /profile/change/name (Web Profile Update Name)

- **Name:** Update Student Name (Web Session)
- **Purpose:** Modifies `users.full_name` and updates the active `HttpSession`.
- **HTTP Method:** `POST`
- **URL:** `/profile/change/name`
- **Authentication:** Website `HttpSession` (verified via `apiToken`).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `fullName` | `string` | Yes | Non-blank, &le; 100 characters | Updated full name |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid name | `{"message": "Name updated successfully.", "fullName": "Praveen Kumar S"}` | Name updated in DB & session |
| `400 Bad Request` | Blank name | `{"message": "Full name cannot be empty."}` | Blank field |
| `400 Bad Request` | Too long | `{"message": "Full name cannot exceed 100 characters."}` | Exceeds 100 chars |
| `401 Unauthorized` | Session expired | `{"message": "Your session has expired. Please sign in again."}` | No active valid session |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/profile/change/name \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"fullName": "Praveen Kumar S"}'
```

---

### 5.2 POST /profile/change/roll-no (Web Profile Update Roll Number)

- **Name:** Update Student Roll Number (Web Session)
- **Purpose:** Modifies `users.roll_number` and updates session cache while enforcing table uniqueness.
- **HTTP Method:** `POST`
- **URL:** `/profile/change/roll-no`
- **Authentication:** Website `HttpSession`.
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `rollNumber` | `string` | Yes | Non-blank, &le; 50 characters | New roll number |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Unique roll number | `{"message": "Roll number updated successfully.", "rollNumber": "2025103622"}` | Updated successfully |
| `400 Bad Request` | Blank or &gt; 50 chars | `{"message": "Roll number cannot be empty."}` | Validation failure |
| `401 Unauthorized` | Unauthenticated | `{"message": "Your session has expired. Please sign in again."}` | Session missing |
| `409 Conflict` | Duplicate roll number | `{"message": "That roll number is already registered to another account."}` | Unique constraint violation |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/profile/change/roll-no \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"rollNumber": "2025103622"}'
```

---

### 5.3 POST /profile/change-password (Web Profile Password Change)

- **Name:** In-App Password Modification
- **Purpose:** Validates existing password, hashes new password with BCrypt, updates DB, revokes old tokens, and issues a fresh session token.
- **HTTP Method:** `POST`
- **URL:** `/profile/change-password`
- **Authentication:** Website `HttpSession`.
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `currentPassword` | `string` | Yes | Non-blank | Existing password |
| `newPassword` | `string` | Yes | &ge; 8 characters | New account password |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Password updated | `{"message": "Password changed successfully."}` | Hash updated; new token issued |
| `400 Bad Request` | Wrong current password | `{"message": "Your current password is incorrect."}` | Authentication check failed |
| `400 Bad Request` | Short new password | `{"message": "The new password must contain at least 8 characters."}` | Minimum length violated |
| `401 Unauthorized` | Unauthenticated | `{"message": "Your session has expired. Please sign in again."}` | Session expired |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/profile/change-password \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "password123",
    "newPassword": "newSecurePassword2026"
  }'
```

---

### 5.4 POST /api/v2/profile/details (API v2 Profile Retrieval)

- **Name:** Fetch Student Profile (API v2)
- **Purpose:** Returns authenticated user profile information.
- **HTTP Method:** `POST`
- **URL:** `/api/v2/profile/details`
- **Authentication:** Opaque Bearer Token (`Authorization: Bearer <TOKEN>`).
- **Headers:** `Authorization: Bearer <TOKEN>`

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid Bearer token | `{"id": 1, "fullName": "Praveen Kumar", "email": "praveen@college.edu", "rollNumber": "2025103621", "createdAt": "2026-10-20T10:00:00Z"}` | Profile details |
| `401 Unauthorized` | Invalid/expired token | `{"message": "Missing, invalid, expired, or revoked token."}` | Access denied |
| `401 Unauthorized` | User missing in DB | `{"message": "User account not found."}` | Record deleted |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v2/profile/details \
  -H "Authorization: Bearer vA7G_wS1-jK09LmNoPqRsTuVwXyZ1234567890abcdef"
```

---

### 5.5 POST /api/v2/profile/change/name (API v2 Update Name)

- **Name:** Update Student Name (API v2)
- **Purpose:** Modifies `users.full_name` via Bearer token authentication.
- **HTTP Method:** `POST`
- **URL:** `/api/v2/profile/change/name`
- **Authentication:** Opaque Bearer Token.
- **Headers:**
  - `Authorization: Bearer <TOKEN>`
  - `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `name` | `string` | Yes | Non-blank, &le; 100 characters | Updated full name |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid name | `{"success": true, "message": "Name updated successfully.", "fullName": "Praveen Kumar S"}` | Name modified |
| `400 Bad Request` | Missing name | `{"message": "Name is required."}` | Blank field |
| `400 Bad Request` | Too long | `{"message": "Name must not exceed 100 characters."}` | Exceeds 100 chars |
| `401 Unauthorized` | Bad token | `{"message": "Missing, invalid, expired, or revoked token."}` | Token rejected |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v2/profile/change/name \
  -H "Authorization: Bearer vA7G_wS1-jK09LmNoPqRsTuVwXyZ1234567890abcdef" \
  -H "Content-Type: application/json" \
  -d '{"name": "Praveen Kumar S"}'
```

---

### 5.6 POST /api/v2/profile/change/roll_no (API v2 Update Roll Number)

- **Name:** Update Student Roll Number (API v2)
- **Purpose:** Modifies `users.roll_number` via Bearer token authentication.
- **HTTP Method:** `POST`
- **URL:** `/api/v2/profile/change/roll_no`
- **Authentication:** Opaque Bearer Token.
- **Headers:**
  - `Authorization: Bearer <TOKEN>`
  - `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `rollNumber` | `string` | Yes | Non-blank, &le; 50 characters | Updated roll number |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid & unique | `{"success": true, "message": "Roll number updated successfully.", "rollNumber": "2025103622"}` | Roll number modified |
| `400 Bad Request` | Missing or &gt; 50 chars | `{"message": "Roll number is required."}` | Length/blank error |
| `401 Unauthorized` | Bad token | `{"message": "Missing, invalid, expired, or revoked token."}` | Auth failure |
| `409 Conflict` | Duplicate | `{"message": "That roll number is already registered."}` | Roll number exists |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/api/v2/profile/change/roll_no \
  -H "Authorization: Bearer vA7G_wS1-jK09LmNoPqRsTuVwXyZ1234567890abcdef" \
  -H "Content-Type: application/json" \
  -d '{"rollNumber": "2025103622"}'
```

---

## 6. Menu and Administrator Endpoints

### 6.1 POST /admin/api/login (Administrator Login)

- **Name:** Administrator Authentication
- **Purpose:** Authenticates the canteen administrator and sets an isolated `ADMINSESSIONID` cookie.
- **HTTP Method:** `POST`
- **URL:** `/admin/api/login`
- **Authentication:** None (Public).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `username` | `string` | Yes | Non-null | Admin username (`app.admin.username`) |
| `password` | `string` | Yes | Non-null | Admin password (`app.admin.password`) |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid credentials | `{"message": "Login successful."}` | `ADMINSESSIONID` cookie issued |
| `400 Bad Request` | Null fields | `{"message": "Username and password are required."}` | Fields missing |
| `401 Unauthorized` | Invalid credentials | `{"message": "Invalid username or password."}` | Credentials do not match |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/admin/api/login \
  -H "Content-Type: application/json" \
  -c admin-cookie.txt \
  -d '{
    "username": "admin",
    "password": "your-admin-password"
  }'
```

#### Sample Response

```http
HTTP/1.1 200 OK
Set-Cookie: ADMINSESSIONID=r9Z2A4B8C1D3E5F7G9H1I3K5L7M9O1P3; Path=/admin; Max-Age=28800; HttpOnly; SameSite=Strict
Content-Type: application/json

{
  "message": "Login successful."
}
```

---

### 6.2 POST /admin/api/logout (Administrator Logout)

- **Name:** Administrator Sign-Out
- **Purpose:** Removes session from memory and clears the `ADMINSESSIONID` cookie.
- **HTTP Method:** `POST`
- **URL:** `/admin/api/logout`
- **Authentication:** Administrator Cookie (`ADMINSESSIONID`).
- **Headers:** None.

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Always | `{"message": "Logged out."}` | Cookie expired with `Max-Age=0` |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/admin/api/logout \
  -b admin-cookie.txt
```

---

### 6.3 GET /admin/api/items (Admin List Menu Items)

- **Name:** Retrieve All Menu Items (Admin)
- **Purpose:** Lists all items in `items` table, including unavailable items.
- **HTTP Method:** `GET`
- **URL:** `/admin/api/items`
- **Authentication:** Administrator Cookie (`ADMINSESSIONID`).
- **Headers:** None.

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid admin session | Array of `MenuItem` objects | Full inventory list |
| `401 Unauthorized` | Missing/expired cookie | `{"status": 401, "error": "Unauthorized", "message": "Admin login required."}` | Access denied |

#### Response Array Structure

```json
[
  {
    "id": "filter-coffee",
    "name": "Filter Coffee",
    "description": "Rich, aromatic coffee for your next break.",
    "category": "Drinks",
    "price": 20.00,
    "imageLink": "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd",
    "available": true
  }
]
```

#### Sample Request

```bash
curl -i -X GET http://localhost:8080/admin/api/items \
  -b admin-cookie.txt
```

---

### 6.4 POST /admin/api/items (Admin Create Menu Item)

- **Name:** Create New Menu Item
- **Purpose:** Inserts a new dish into the `items` table.
- **HTTP Method:** `POST`
- **URL:** `/admin/api/items`
- **Authentication:** Administrator Cookie (`ADMINSESSIONID`).
- **Headers:** `Content-Type: application/json`

#### Request Body

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `string` | Yes | Non-blank, &le; 60 chars | Unique item slug (e.g., `filter-coffee`) |
| `name` | `string` | Yes | Non-blank, &le; 120 chars | Item display name |
| `description`| `string` | No | &le; 500 chars | Item description (blank saved as `NULL`) |
| `category` | `string` | Yes | Non-blank, &le; 50 chars | Item category (e.g., `Drinks`, `Meals`) |
| `price` | `number` | Yes | &ge; 0.00, scale &le; 2, precision &le; 10 | Price in INR |
| `imageLink` | `string` | No | &le; 2048 chars | URL to dish picture |
| `available` | `boolean` | Yes | `true` or `false` | Visibility on customer menu |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `201 Created` | Valid item | `{"message": "Item created successfully."}` | Item saved in `items` table |
| `400 Bad Request` | Missing required fields | `{"message": "ID, name, category and a non-negative price are required."}` | Validation failure |
| `400 Bad Request` | Field length exceeded | `{"message": "One or more fields are too long or invalid."}` | Exceeds DB column width |
| `401 Unauthorized` | Missing admin session | `{"message": "Admin login required."}` | Unauthorized |
| `409 Conflict` | Duplicate ID | `{"message": "An item with that ID already exists."}` | Primary key collision |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/admin/api/items \
  -b admin-cookie.txt \
  -H "Content-Type: application/json" \
  -d '{
    "id": "veg-sandwich",
    "name": "Grilled Veg Sandwich",
    "description": "Toasted bread with seasoned garden vegetables.",
    "category": "Quick bites",
    "price": 40.00,
    "imageLink": "https://images.unsplash.com/photo-1528735602780-2552fd46c7af",
    "available": true
  }'
```

---

### 6.5 PUT /admin/api/items/{oldId} (Admin Update Menu Item)

- **Name:** Update Existing Menu Item
- **Purpose:** Updates attributes of an existing menu item, including the ability to change its primary key ID.
- **HTTP Method:** `PUT`
- **URL:** `/admin/api/items/{oldId}`
- **Path Variables:** `oldId` (`string`, required) — Current ID of the item being updated.
- **Authentication:** Administrator Cookie (`ADMINSESSIONID`).
- **Headers:** `Content-Type: application/json`

#### Request Body
*Same schema as `POST /admin/api/items`.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid update | `{"message": "Item updated successfully."}` | Row updated |
| `400 Bad Request` | Validation failure | `{"message": "ID, name, category and a non-negative price are required."}` | Validation error |
| `401 Unauthorized` | Missing admin session | `{"message": "Admin login required."}` | Unauthorized |
| `404 Not Found` | Non-existent `oldId` | `{"message": "Item not found."}` | No matching row |
| `409 Conflict` | New ID already used | `{"message": "That item ID is already in use."}` | Target ID belongs to another item |

#### Sample Request

```bash
curl -i -X PUT http://localhost:8080/admin/api/items/veg-sandwich \
  -b admin-cookie.txt \
  -H "Content-Type: application/json" \
  -d '{
    "id": "veg-cheese-sandwich",
    "name": "Grilled Veg Cheese Sandwich",
    "description": "Toasted bread with garden vegetables and melted cheese.",
    "category": "Quick bites",
    "price": 50.00,
    "imageLink": "",
    "available": true
  }'
```

---

### 6.6 DELETE /admin/api/items/{id} (Admin Delete Menu Item)

- **Name:** Delete Menu Item
- **Purpose:** Deletes an item from the `items` table.
- **HTTP Method:** `DELETE`
- **URL:** `/admin/api/items/{id}`
- **Path Variables:** `id` (`string`, required) — Target item ID.
- **Authentication:** Administrator Cookie (`ADMINSESSIONID`).
- **Headers:** None.

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Item deleted | `{"message": "Item deleted successfully."}` | Row deleted from `items` |
| `401 Unauthorized` | Missing admin session | `{"message": "Admin login required."}` | Unauthorized |
| `404 Not Found` | Item not found | `{"message": "Item not found."}` | ID does not exist |

#### Sample Request

```bash
curl -i -X DELETE http://localhost:8080/admin/api/items/veg-cheese-sandwich \
  -b admin-cookie.txt
```

---

### 6.7 POST /admin/orders/{orderId}/status (Admin Order Status Update)

- **Name:** Update Paid Order Lifecycle Status
- **Purpose:** Transitions a paid order's status through the strict kitchen lifecycle.
- **HTTP Method:** `POST`
- **URL:** `/admin/orders/{orderId}/status`
- **Path Variables:** `orderId` (`long`, required) — Master order ID.
- **Request Parameters (Form/Query):**
  - `status` (`string`, required) — New status. Permitted values: `PREPARING`, `READY`, `COMPLETED`, `CANCELLED`.
- **Authentication:** Administrator Cookie (`ADMINSESSIONID`).
- **Headers:** `Content-Type: application/x-www-form-urlencoded`

#### Lifecycle State Rules
```mermaid
stateDiagram-v2
    PLACED --> PREPARING: Admin Starts Cooking
    PLACED --> CANCELLED: Out of Stock
    PREPARING --> READY: Food Prepared
    PREPARING --> CANCELLED: Cancelled
    READY --> COMPLETED: Customer Picked Up
    READY --> CANCELLED: Unclaimed
```

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `302 Found` | Allowed transition | *(Redirects to `/admin/orders`)* | Flash: `Order #X updated to Y.` |
| `302 Found` | Invalid status or transition | *(Redirects to `/admin/orders`)* | Flash: `Order not found, not paid, or the status transition is not allowed.` |
| `401 Unauthorized` | Missing admin session | `{"message": "Admin login required."}` | Unauthorized |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/admin/orders/11/status \
  -b admin-cookie.txt \
  -d "status=PREPARING"
```

---

## 7. Order and Checkout Endpoints

### 7.1 POST /order/checkout (Cart Submission & Order Placement)

- **Name:** Authoritative Checkout & Order Creation
- **Purpose:** Takes client cart lines, resolves authoritative prices from `items`, computes total on server, and creates `orders` and `order_items` entries with status `PLACED`.
- **HTTP Method:** `POST`
- **URL:** `/order/checkout`
- **Authentication:** Website `HttpSession` (requires `userId`).
- **Headers:** `Content-Type: application/x-www-form-urlencoded`

#### Request Body (Form Encoded)

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `items[i].itemId` | `string` | Yes | Non-blank, must exist in `items` table with `available = TRUE` | Unique item slug ID |
| `items[i].quantity` | `integer`| Yes | 1 &le; quantity &le; 100, combined per item &le; 100 | Units ordered |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Order created | HTML View: `checkout-bill.html` | Order inserted; bill rendered with `orderId` |
| `200 OK` | Validation error | HTML View: `checkout-error.html` | Empty cart, quantity &gt; 100, or item unavailable |
| `302 Found` | Unauthenticated | Redirects to `/auth/login?returnUrl=/cart` | User must sign in |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/order/checkout \
  -b cookies.txt \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "items[0].itemId=filter-coffee&items[0].quantity=2&items[1].itemId=brownie&items[1].quantity=1"
```

---

## 8. Payment Endpoints

### 8.1 POST /payment/bill (Generate Payment Bill)

- **Name:** Generate Pending Payment Record
- **Purpose:** Locks the order row with `FOR UPDATE`, verifies items in `order_items`, generates a unique `CB-...` transaction ID, creates a `payments` record (`PENDING`), and renders the payment gateway screen.
- **HTTP Method:** `POST`
- **URL:** `/payment/bill`
- **Authentication:** Website `HttpSession` (must match `orders.user_id`).
- **Headers:** `Content-Type: application/x-www-form-urlencoded`

#### Request Parameters (Form Encoded)

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `orderId` | `long` | Yes | Must exist and belong to user | Order ID generated during checkout |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Valid order | HTML View: `payment-bill.html` | Bill rendered with `transactionId` and amount |
| `200 OK` | Order not found / mismatch | HTML View: `checkout-error.html` | Error displayed to student |
| `302 Found` | Unauthenticated | Redirects to `/auth/login?returnUrl=/cart` | User not signed in |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/payment/bill \
  -b cookies.txt \
  -d "orderId=11"
```

---

### 8.2 POST /payment/pay (Simulate Payment Execution)

- **Name:** Execute Simulated Payment
- **Purpose:** Simulates third-party bank approval. Transitions `payments.status` from `PENDING` to `PAID` and sets `paid_at = CURRENT_TIMESTAMP`.
- **HTTP Method:** `POST`
- **URL:** `/payment/pay`
- **Authentication:** None (Deliberately unauthenticated; verifies via `transactionId`).
- **Headers:** `Content-Type: application/x-www-form-urlencoded`

#### Request Parameters (Form Encoded)

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `transactionId` | `string` | Yes | Non-blank, must be `PENDING` | `CB-...` identifier from `/payment/bill` |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Payment successful | HTML View: `payment-redirect-confirmation.html` | Payment marked `PAID`; auto-submits to confirmation |
| `200 OK` | Already paid / missing ID | HTML View: `checkout-error.html` | Error: `Transaction not found or already processed.` |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/payment/pay \
  -d "transactionId=CB-9JV8nxdv8ZHy8pInis7SDZ-qivWJ0Phip56cpGUJ-zU"
```

---

### 8.3 POST /order/confirmation (Order Confirmation Receipt)

- **Name:** Order Receipt Confirmation
- **Purpose:** Verifies that both the order and payment transaction match and are marked `PAID`. Renders the final student order receipt.
- **HTTP Method:** `POST`
- **URL:** `/order/confirmation`
- **Authentication:** None (Deliberately unauthenticated; verifies relationship between `orderId` and `transactionId`).
- **Headers:** `Content-Type: application/x-www-form-urlencoded`

#### Request Parameters (Form Encoded)

| Field | Data type | Required | Constraints | Description |
| :--- | :--- | :---: | :--- | :--- |
| `orderId` | `long` | Yes | Must reference existing order | Order ID |
| `transactionId` | `string` | Yes | Must reference linked `PAID` payment | Transaction ID |

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Verification successful | HTML View: `order-confirmation.html` | Final receipt rendered |
| `200 OK` | Verification failed | HTML View: `checkout-error.html` | Error: `The order and paid transaction could not be verified.` |

#### Sample Request

```bash
curl -i -X POST http://localhost:8080/order/confirmation \
  -d "orderId=11&transactionId=CB-9JV8nxdv8ZHy8pInis7SDZ-qivWJ0Phip56cpGUJ-zU"
```

---

## 9. Other Implemented Endpoints

### 9.1 GET /api/v1/ (System Health & Version)

- **Name:** System Health Check
- **Purpose:** Public endpoint verifying that the Spring Boot WebMVC application is running.
- **HTTP Method:** `GET`
- **URL:** `/api/v1/`
- **Authentication:** None (Public).
- **Headers:** None.

#### Request Body
*None.*

#### Responses

| HTTP status | Scenario | Response body | Meaning |
| :--- | :--- | :--- | :--- |
| `200 OK` | Service operational | `{"version": "1.0", "status": "working"}` | Service is running |

#### Sample Request

```bash
curl -i -X GET http://localhost:8080/api/v1/
```

---

## 10. End-to-End Request Workflows

### Workflow 1: User Registration, Sign-In & Profile Updates

This workflow demonstrates registering a student account, logging in via API v2, and updating student profile fields.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant API as Spring Boot API
    participant DB as MySQL Database

    Client->>API: 1. POST /api/v1/auth/register {fullName, email, rollNumber, password, confirmPassword}
    API->>DB: INSERT INTO users (...)
    API-->>Client: 201 Created {"message": "Account created successfully..."}

    Client->>API: 2. POST /api/v2/auth/login {email, password}
    API->>DB: SELECT password_hash FROM users WHERE email = ?
    API->>DB: INSERT INTO api_tokens (token_hash, expires_at)
    API-->>Client: 200 OK {"token": "<RAW_TOKEN>"}

    Client->>API: 3. POST /api/v2/profile/details (Header: Bearer <RAW_TOKEN>)
    API->>DB: SELECT user_id FROM api_tokens ...
    API-->>Client: 200 OK {id, fullName, email, rollNumber, createdAt}

    Client->>API: 4. POST /api/v2/profile/change/name {name: "New Name"} (Header: Bearer <RAW_TOKEN>)
    API->>DB: UPDATE users SET full_name = ? WHERE id = ?
    API-->>Client: 200 OK {"success": true, "fullName": "New Name"}
```

---

### Workflow 2: Menu Browsing, Authoritative Checkout & Payment Simulation

This workflow traces the complete student pre-order lifecycle from cart submission to confirmed order receipt.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant OrderCtrl as OrderController
    participant PayCtrl as PaymentController
    participant DB as MySQL Database

    Note over Student: Student signs in and stores JSESSIONID in cookies.txt

    Student->>OrderCtrl: 1. POST /order/checkout (items[0].itemId=brownie&items[0].quantity=2)
    OrderCtrl->>DB: SELECT price FROM items WHERE id = ? AND available = TRUE
    OrderCtrl->>DB: INSERT INTO orders (user_id, total_amount, status='PLACED')
    OrderCtrl->>DB: INSERT INTO order_items (order_id, product_id, unit_price, quantity)
    OrderCtrl-->>Student: 200 OK (Renders checkout-bill.html with orderId=11)

    Student->>PayCtrl: 2. POST /payment/bill (orderId=11)
    PayCtrl->>DB: SELECT ... FROM orders WHERE id = 11 FOR UPDATE
    PayCtrl->>DB: INSERT INTO payments (transaction_id='CB-...', amount=90.00, status='PENDING')
    PayCtrl->>DB: UPDATE orders SET payment_id = ?, transaction_id = 'CB-...' WHERE id = 11
    PayCtrl-->>Student: 200 OK (Renders payment-bill.html with transactionId='CB-...')

    Student->>PayCtrl: 3. POST /payment/pay (transactionId='CB-...')
    PayCtrl->>DB: UPDATE payments SET status='PAID', paid_at=NOW() WHERE transaction_id='CB-...'
    PayCtrl-->>Student: 200 OK (Renders payment-redirect-confirmation.html)

    Student->>PayCtrl: 4. POST /order/confirmation (orderId=11, transactionId='CB-...')
    PayCtrl->>DB: SELECT ... FROM orders JOIN payments ... WHERE p.status='PAID'
    PayCtrl-->>Student: 200 OK (Renders order-confirmation.html receipt)
```

---

### Workflow 3: Administrator Menu Management & Order Progression

This workflow illustrates how canteen staff authenticate, add a new dish to the catalog, and progress paid customer orders.

1. **Authenticate Administrator:**
   ```bash
   curl -i -X POST http://localhost:8080/admin/api/login \
     -H "Content-Type: application/json" \
     -c admin-cookie.txt \
     -d '{"username": "admin", "password": "your-admin-password"}'
   ```
2. **Add Menu Item:**
   ```bash
   curl -i -X POST http://localhost:8080/admin/api/items \
     -b admin-cookie.txt \
     -H "Content-Type: application/json" \
     -d '{
       "id": "masala-dosa",
       "name": "Masala Dosa",
       "description": "Crispy crepe with spicy potato filling.",
       "category": "Meals",
       "price": 55.00,
       "imageLink": "",
       "available": true
     }'
   ```
3. **Inspect Menu Items:**
   ```bash
   curl -i -X GET http://localhost:8080/admin/api/items \
     -b admin-cookie.txt
   ```
4. **Advance Order Status to PREPARING:**
   ```bash
   curl -i -X POST http://localhost:8080/admin/orders/11/status \
     -b admin-cookie.txt \
     -d "status=PREPARING"
   ```
5. **Advance Order Status to READY:**
   ```bash
   curl -i -X POST http://localhost:8080/admin/orders/11/status \
     -b admin-cookie.txt \
     -d "status=READY"
   ```
6. **Mark Order as COMPLETED:**
   ```bash
   curl -i -X POST http://localhost:8080/admin/orders/11/status \
     -b admin-cookie.txt \
     -d "status=COMPLETED"
   ```

---

### Workflow 4: Self-Service Password Recovery via Email Token

1. **Trigger Password Recovery:**
   ```bash
   curl -i -X POST http://localhost:8080/auth/forgot-password \
     -H "Content-Type: application/json" \
     -d '{"email": "praveen@college.edu"}'
   ```
   *Action:* System checks database, generates 32-byte Base64 URL-safe token, hashes with SHA-256 into `password_reset_tokens` (30-minute expiry), and sends email with link:
   `http://localhost:8080/auth/reset-password?token=<RAW_TOKEN>`
2. **Submit New Password with Token:**
   ```bash
   curl -i -X POST http://localhost:8080/auth/reset-password \
     -H "Content-Type: application/json" \
     -d '{
       "token": "<RAW_TOKEN_FROM_EMAIL>",
       "newPassword": "myNewPassword2026"
     }'
   ```
   *Action:* System validates token with `FOR UPDATE`, marks token used (`used_at = NOW()`), updates `users.password_hash` with BCrypt, and revokes all active API tokens for that user.

---

## 11. Error Handling and Status Codes

The application employs consistent HTTP response codes across JSON APIs and HTML form handlers:

| Status Code | Meaning | Typical Scenarios |
| :--- | :--- | :--- |
| `200 OK` | Success | Successful GET requests, JSON profile updates, and successful payment operations. |
| `201 Created` | Resource Created | Account registered (`/api/v1/auth/register`) or menu item created (`/admin/api/items`). |
| `302 Found` | Redirection | Unauthenticated web redirects (`/auth/login?returnUrl=...`), logout redirect to `/`, and admin order status updates redirecting to `/admin/orders`. |
| `400 Bad Request` | Client Error | Missing required fields, validation failures, passwords under 8 characters, or fields exceeding database column limits. |
| `401 Unauthorized` | Auth Failure | Invalid login credentials, missing or revoked API tokens, expired reset tokens, or missing `ADMINSESSIONID` cookies. |
| `404 Not Found` | Not Found | Item ID not found during PUT/DELETE operations, or unmapped web URLs (renders `error/404.html`). |
| `409 Conflict` | Conflict | Duplicate email or roll number (`users` table) or duplicate menu item ID (`items` table). |
| `500 Server Error` | Server Exception | Uncaught runtime exceptions, database connectivity failure, or SMTP mail exceptions. |
