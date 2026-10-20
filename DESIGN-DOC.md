# CampusBite — Software Design Document

## 1. Problem Statement

In academic institutions, campus canteens experience intense bursts of demand during short intervals between lectures, lunch breaks, and evening hours. Traditional canteen operations rely on physical walk-in queues, manual paper ordering, verbal communication at preparation counters, and cash or ad-hoc UPI payments. This setup causes significant operational bottlenecks: students face prolonged wait times and crowd congestion, while canteen personnel struggle with order misplacement, lack of real-time inventory visibility, and manual reconciliation between receipts and kitchen preparation queues.

**CampusBite** is a campus canteen ordering system built to streamline pre-ordering, kitchen queue coordination, and digital transactions. It serves two distinct user roles:

1. **Students:** Need to browse active food and beverage offerings, assemble orders via an interactive shopping cart, checkout with server-verified prices, execute payments through an automated workflow, receive verified order confirmations, track order history, and self-manage account credentials.
2. **Canteen Administrators:** Need a dedicated portal to manage menu inventory (adding, updating prices, modifying availability flags, or deleting items) and monitor a priority-sorted kitchen fulfillment queue to transition orders through defined preparation stages.

### Scope and Limitations of the Implementation
The current implementation is an academic monolithic application designed for a single campus canteen. The system implements a simulated payment processing workflow rather than integrating a live commercial payment gateway. Menu availability is tracked via a binary active/inactive flag rather than real-time numerical stock decrementing. Client-side carts are stored in browser local storage, and administrator authentication uses an in-memory session registry rather than persistent clustered storage.

---

## 2. Layered Architecture

CampusBite follows a multi-tier layered architecture combining server-rendered web views (Thymeleaf) and stateless JSON REST endpoints on top of a Spring Boot core. The system deliberately uses Spring's `JdbcTemplate` for direct SQL data access rather than an object-relational mapping (ORM) framework like Hibernate or JPA.

```mermaid
flowchart TD
    subgraph ClientTier["1. Client / Presentation Layer"]
        Browser["Web Browser<br>(Thymeleaf Views, HTML5, Vanilla JS Cart)"]
        ApiClient["REST API Clients<br>(Mobile App, cURL, HTTP Clients)"]
    end

    subgraph SecurityTier["2. Security & Cross-Cutting Layer"]
        SecConfig["Spring Security Filter Chain<br>(Global permitAll, CSRF Disabled)"]
        Interceptors["SessionRefreshInterceptor<br>(Pre-Handle DB Token Validation)"]
        PassEncoder["BCryptPasswordEncoder<br>(Salted Password Hashing)"]
        AdminMap["Admin Session Registry<br>(ConcurrentHashMap In-Memory Store)"]
    end

    subgraph ControllerTier["3. Controller Layer"]
        PageCtrl["PageController<br>(View Routing)"]
        WebAuthCtrl["WebsiteAuthController &<br>WebsitePasswordResetController"]
        WebProfileCtrl["WebsiteProfileController"]
        MenuCtrl["MenuController"]
        OrderCtrl["OrderController"]
        PayCtrl["PaymentController"]
        HistCtrl["OrderHistoryController"]
        AdminCtrl["AdminController"]
        AdminOrderCtrl["AdminOrderController"]
        ApiV1Ctrl["ApiController & AuthController (v1)"]
        ApiV2Ctrl["V2AuthController & V2ProfileController (v2)"]
    end

    subgraph ServiceTier["4. Business Logic & Service Layer"]
        AuthServ["AuthService<br>(Account, Reset & Password Logic)"]
        TokenServ["ApiTokenService<br>(256-bit Token Generation & SHA-256 Hashing)"]
        RefreshServ["SessionRefreshService<br>(Session Attribute Synchronization)"]
        CatalogServ["MenuCatalogService<br>(Static Catalog Reference)"]
        TxBoundary["In-Controller Transactions<br>(@Transactional JDBC Workflows)"]
    end

    subgraph DataAccessTier["5. Data Access Layer"]
        Jdbc["Spring JdbcTemplate<br>(Parameterized SQL, RowMappers, GeneratedKeyHolder)"]
    end

    subgraph PersistenceTier["6. Persistence Layer"]
        DB[(MySQL Database<br>campus_bite)]
    end

    Browser -->|HTTP GET/POST Forms & Cookies| SecConfig
    ApiClient -->|HTTP JSON & Bearer Tokens| SecConfig

    SecConfig --> Interceptors
    Interceptors --> ControllerTier

    PageCtrl --> RefreshServ
    PageCtrl --> AuthServ
    WebAuthCtrl --> AuthServ
    WebAuthCtrl --> TokenServ
    WebAuthCtrl --> RefreshServ
    WebProfileCtrl --> AuthServ
    WebProfileCtrl --> TokenServ
    MenuCtrl --> Jdbc
    OrderCtrl --> Jdbc
    PayCtrl --> Jdbc
    HistCtrl --> Jdbc
    AdminCtrl --> Jdbc
    AdminOrderCtrl --> AdminCtrl
    AdminOrderCtrl --> Jdbc
    ApiV1Ctrl --> AuthServ
    ApiV2Ctrl --> AuthServ
    ApiV2Ctrl --> TokenServ

    AuthServ --> PassEncoder
    AuthServ --> Jdbc
    TokenServ --> Jdbc
    RefreshServ --> TokenServ
    RefreshServ --> AuthServ

    ControllerTier --> TxBoundary
    TxBoundary --> Jdbc
    Jdbc -->|TCP / MySQL Connector 9.2.0| DB
```

### Architectural Component Descriptions

1. **Client / Presentation Layer:**
   - **Thymeleaf Templates:** Server-side rendered HTML views (`index.html`, `menu.html`, `cart.html`, `checkout-bill.html`, `payment-bill.html`, `order-confirmation.html`, `orders.html`, `profile.html`, `admin.html`, `admin-orders.html`).
   - **Client-Side JavaScript (`canteen.js`):** Manages shopping cart operations in browser `localStorage`, calculates item subtotals, and dynamically submits form structures to backend controllers.
   - **REST Consumers:** Mobile or third-party HTTP clients communicating via JSON payloads and Bearer headers.

2. **Security & Cross-Cutting Components:**
   - **Spring Security (`SecurityConfig`):** Configured to allow all requests (`permitAll()`) and disable default CSRF/form-login filters, delegating authentication to application-level handlers.
   - **`SessionRefreshInterceptor`:** Registered via `WebMvcConfigurer` on paths `/`, `/menu`, `/cart`, and `/profile`. Intercepts incoming requests, retrieves the session's `apiToken`, and queries the database to synchronize session attributes or invalidate revoked sessions.
   - **Cryptographic Security (`BCryptPasswordEncoder` & `ApiTokenService`):** Uses BCrypt with adaptive salting for passwords and `SecureRandom` 256-bit generation with SHA-256 digests for API bearer tokens.
   - **Admin Isolation Registry (`AdminController`):** Maintains administrator sessions in an in-memory `ConcurrentHashMap<String, AdminSession>` using custom `ADMINSESSIONID` cookies, completely isolated from standard student servlet sessions.

3. **Controller Layer:**
   Spring MVC `@Controller` and `@RestController` classes map endpoints, extract parameters, perform input validation, and assemble view models or JSON responses. Notably, several controllers (such as `OrderController`, `PaymentController`, `MenuController`, and `AdminOrderController`) coordinate data operations directly using `JdbcTemplate` rather than routing through an intermediary service interface.

4. **Business Logic & Service Layer:**
   - `AuthService`: Encapsulates user registration, password credential verification, timing-safe password reset generation, token consumption with row locking, and profile updates.
   - `ApiTokenService`: Manages creation, verification, SHA-256 hashing, and revocation of 5-day Bearer tokens.
   - `SessionRefreshService`: Bridges servlet `HttpSession` attributes with database user state.
   - `MenuCatalogService`: Provides a static in-memory fallback catalog of standard cafeteria offerings.
   - In-Controller Business Logic: Order creation, price calculation, and payment progression logic reside directly in transactional controller methods.

5. **Data Access Layer:**
   All persistence is handled using Spring's core `JdbcTemplate`. Queries are expressed as parameterized raw SQL statements to prevent SQL injection. Auto-generated primary keys are retrieved via `GeneratedKeyHolder`. The application does not use JPA entities, Hibernate sessions, or Spring Data repositories.

6. **Persistence Layer:**
   A relational MySQL 8.0+ database (`campus_bite`) enforcing relational integrity through foreign keys, check constraints, unique indexes, and InnoDB transaction isolation.

---

## 3. Data Model: ER Diagram and Entity Descriptions

The application data model consists of seven relational tables in MySQL.

```mermaid
erDiagram
    USERS ||--o{ API_TOKENS : "issues"
    USERS ||--o{ PASSWORD_RESET_TOKENS : "requests"
    USERS ||--o{ ORDERS : "places"
    USERS ||--o{ PAYMENTS : "initiates"
    ORDERS ||--o{ ORDER_ITEMS : "contains"
    PAYMENTS ||--o| ORDERS : "settles"

    USERS {
        bigint id PK
        varchar(100) full_name
        varchar(255) email UK
        varchar(50) roll_number UK
        varchar(255) password_hash
        timestamp created_at
    }

    API_TOKENS {
        bigint id PK
        bigint user_id FK
        char(64) token_hash UK
        timestamp expires_at
        timestamp revoked_at
        timestamp created_at
    }

    PASSWORD_RESET_TOKENS {
        bigint id PK
        bigint user_id FK
        char(64) token_hash UK
        timestamp expires_at
        timestamp used_at
        timestamp created_at
    }

    ITEMS {
        varchar(60) id PK
        varchar(120) name
        varchar(500) description
        varchar(50) category
        decimal(10_2) price
        varchar(2048) image_link
        boolean available
        timestamp created_at
    }

    ORDERS {
        bigint id PK
        bigint user_id FK
        bigint payment_id FK, UK
        varchar(80) transaction_id FK, UK
        decimal(10_2) total_amount
        enum status
        timestamp created_at
    }

    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        varchar(60) product_id
        varchar(120) product_name
        decimal(10_2) unit_price
        int quantity
    }

    PAYMENTS {
        bigint id PK
        varchar(80) transaction_id UK
        bigint user_id FK
        decimal(10_2) amount
        enum status
        timestamp created_at
        timestamp paid_at
    }
```

### Entity Description Table

| Entity / Table | Purpose | Primary Key | Important Relationships |
| :--- | :--- | :--- | :--- |
| **`users`** | Stores registered student accounts, credentials, and academic roll numbers. | `id` (BIGINT Auto-Increment) | One-to-Many with `api_tokens`, `password_reset_tokens`, `orders`, and `payments`. `email` and `roll_number` are unique. |
| **`api_tokens`** | Stores SHA-256 digests of active and revoked 5-day Bearer tokens. | `id` (BIGINT Auto-Increment) | Many-to-One with `users` via `user_id` (`ON DELETE CASCADE`). Plaintext tokens are not stored. |
| **`password_reset_tokens`** | Stores single-use SHA-256 digests of 30-minute password recovery tokens. | `id` (BIGINT Auto-Increment) | Many-to-One with `users` via `user_id` (`ON DELETE CASCADE`). Tracks consumption via `used_at`. |
| **`items`** | Stores cafeteria food and beverage catalog offerings and retail prices. | `id` (VARCHAR(60) Slug) | Natural primary key (e.g. `masala-dosa`). Flagged active/inactive via `available`. Price constrained (`CHECK price >= 0`). |
| **`orders`** | Master pre-order record tracking fulfillment state and billing total. | `id` (BIGINT Auto-Increment) | Many-to-One with `users` via `user_id`. One-to-One (0..1) with `payments` via `payment_id` and `transaction_id`. Status is an enum (`PLACED`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED`). |
| **`order_items`** | Immutable financial line item snapshots captured at the moment of order placement. | `id` (BIGINT Auto-Increment) | Many-to-One with `orders` via `order_id` (`ON DELETE CASCADE`). Captures `product_name` and `unit_price` at checkout. |
| **`payments`** | Records financial transactions, payment statuses, and verification timestamps. | `id` (BIGINT Auto-Increment) | Many-to-One with `users` via `user_id`. Referenced by `orders.payment_id` and `orders.transaction_id`. Status is an enum (`PENDING`, `PAID`, `FAILED`). |

### Key Structural & Relational Characteristics
1. **Financial Snapshot Pattern:** `order_items` stores historical copies of `product_name` and `unit_price` alongside `product_id`. It does not define an active foreign key constraint to `items(id)`. This prevents administrative price updates or product deletions in `items` from retroactively altering historic customer bills or causing cascade deletion failures.
2. **Nullable Order-to-Payment Association:** When an order is initially created via `/order/checkout`, its `payment_id` and `transaction_id` columns in `orders` are `NULL`, with status set to `PLACED`. The payment record is established only when the student proceeds to `/payment/bill`.
3. **Defense-in-Depth Token Persistence:** Neither `api_tokens` nor `password_reset_tokens` persist raw client tokens. Only their 64-character hexadecimal SHA-256 digests are stored, preventing credential replay if database tables are exposed.

---

## 4. UML Sequence Diagrams for All Implemented Flows

The following diagrams illustrate the dynamic execution of all implemented end-to-end flows.

### 4.1 Student Registration

Handles new student account creation, validates input fields, verifies password confirmation, enforces uniqueness rules, and saves the BCrypt password hash.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant Ctrl as AuthController
    participant Auth as AuthService
    participant Enc as PasswordEncoder
    participant DB as MySQL Database

    Student->>Ctrl: POST /api/v1/auth/register {fullName, email, rollNumber, password, confirmPassword}
    
    alt Validation failure (missing fields or password < 8 chars)
        Ctrl-->>Student: 400 Bad Request {"message": "Please fill in all required fields."}
    else Passwords do not match
        Ctrl-->>Student: 400 Bad Request {"message": "Passwords do not match."}
    else Valid input
        Ctrl->>Auth: register(fullName, email, rollNumber, password)
        Auth->>Enc: encode(password)
        Enc-->>Auth: passwordHash (BCrypt $2a$...)
        Auth->>DB: INSERT INTO users (full_name, email, roll_number, password_hash) VALUES (?, ?, ?, ?)
        
        alt Duplicate email or roll number (Unique Constraint Violation)
            DB-->>Auth: DuplicateKeyException
            Auth-->>Ctrl: DuplicateKeyException
            Ctrl-->>Student: 409 Conflict {"message": "That email or roll number is already registered."}
        else Successful insert
            DB-->>Auth: 1 row affected
            Auth-->>Ctrl: void
            Ctrl-->>Student: 201 Created {"message": "Account created successfully. Please sign in.", "redirectUrl": "/auth/login"}
        end
    end
```

---

### 4.2 Student Login and Authentication

CampusBite supports two distinct login paths: stateful web session login and stateless API v2 Bearer token login.

#### 4.2.1 Web Session Login (`POST /auth/login`)
Authenticates credentials, mitigates session fixation, generates an API token in the database, and stores session attributes.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant Ctrl as WebsiteAuthController
    participant Auth as AuthService
    participant Token as ApiTokenService
    participant DB as MySQL Database
    participant Session as HttpSession

    Student->>Ctrl: POST /auth/login {email, password}
    Ctrl->>Auth: signIn(email, password)
    Auth->>DB: SELECT password_hash FROM users WHERE email = ?
    DB-->>Auth: storedHash
    Auth-->>Ctrl: authenticated (true/false)

    alt Invalid credentials
        Ctrl-->>Student: 401 Unauthorized {"message": "Invalid email or password."}
    else Valid credentials
        Ctrl->>Auth: findByEmail(email)
        Auth->>DB: SELECT id, full_name, email, roll_number FROM users WHERE email = ?
        DB-->>Auth: user record
        Auth-->>Ctrl: UserDetails(id, fullName, email, rollNumber)

        Ctrl->>Token: generateToken(userId)
        Token->>DB: INSERT INTO api_tokens (user_id, token_hash, expires_at) VALUES (?, SHA-256, NOW + 5 days)
        Token-->>Ctrl: rawToken

        Note over Ctrl,Session: Fixation Defense: Invalidate existing session if present
        Ctrl->>Session: oldSession.invalidate()
        Ctrl->>Session: newSession.setAttribute("apiToken", rawToken)
        Ctrl->>Session: newSession.setAttribute("userId", user.id)
        Ctrl->>Session: newSession.setAttribute("fullName", user.fullName)
        
        Ctrl-->>Student: 200 OK {"message": "Login successful.", "redirectUrl": "/"} (Sets JSESSIONID cookie)
    end
```

#### 4.2.2 API v2 Stateless Bearer Login (`POST /api/v2/auth/login`)
Designed for mobile and API clients. Returns an opaque token in the JSON response without creating a servlet session.

```mermaid
sequenceDiagram
    autonumber
    actor Client as API Client
    participant Ctrl as V2AuthController
    participant Auth as AuthService
    participant Token as ApiTokenService
    participant DB as MySQL Database

    Client->>Ctrl: POST /api/v2/auth/login {email, password}
    Ctrl->>Auth: signIn(email, password)
    Auth->>DB: SELECT password_hash FROM users WHERE email = ?
    DB-->>Auth: hash
    Auth-->>Ctrl: authenticated (true/false)

    alt Authentication failed
        Ctrl-->>Client: 401 Unauthorized {"message": "Invalid email or password."}
    else Authentication successful
        Ctrl->>Auth: findByEmail(email)
        Auth-->>Ctrl: UserDetails(userId, ...)
        Ctrl->>Token: generateToken(userId)
        Token->>DB: INSERT INTO api_tokens (user_id, token_hash, expires_at) VALUES (?, SHA-256, +5 days)
        Token-->>Ctrl: rawToken
        Ctrl-->>Client: 200 OK {"token": "<RAW_TOKEN>"}
    end
```

---

### 4.3 Menu Browsing and Cart Management

Shows how active menu items are rendered from MySQL and how the shopping cart operates on the client side using `localStorage` without generating server load.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant Browser as Client Browser (canteen.js)
    participant MenuCtrl as MenuController
    participant DB as MySQL Database

    Student->>Browser: Navigates to /menu
    Browser->>MenuCtrl: GET /menu
    MenuCtrl->>DB: SELECT id, name, description, category, price, image_link, available FROM items WHERE available = TRUE ORDER BY category, name
    DB-->>MenuCtrl: List<MenuItem>
    MenuCtrl-->>Browser: 200 OK (Renders menu.html with items)

    Note over Student,Browser: Client-Side Cart Operations (Zero Server Requests)
    Student->>Browser: Clicks "Add to Cart" (e.g. Filter Coffee)
    Browser->>Browser: readCart() from localStorage
    Browser->>Browser: Update quantity & calculate line subtotal
    Browser->>Browser: saveCart() to localStorage ("campusbite-cart")
    Browser->>Browser: Update navbar cart count badge (#navCartCount)

    Student->>Browser: Navigates to /cart
    Browser->>Browser: Reads localStorage, builds DOM table, and renders cart.html
```

---

### 4.4 Checkout and Order Creation

Traces cart submission, server-side price lookup, item deduplication, and atomic insertion into `orders` and `order_items`.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant OrderCtrl as OrderController
    participant DB as MySQL Database

    Student->>OrderCtrl: POST /order/checkout (Form: items[0].itemId=coffee&items[0].quantity=2)

    alt Session missing or invalid
        OrderCtrl-->>Student: 302 Redirect to /auth/login?returnUrl=/cart
    else Authenticated
        OrderCtrl->>DB: SELECT COUNT(*) FROM users WHERE id = ?
        DB-->>OrderCtrl: count (1)

        alt Cart empty or invalid item quantity (> 100)
            OrderCtrl-->>Student: 200 OK (Renders checkout-error.html)
        else Valid cart lines
            Note over OrderCtrl: Consolidate duplicate item IDs in memory

            loop For each unique item ID
                OrderCtrl->>DB: SELECT id, name, price FROM items WHERE id = ? AND available = TRUE
                DB-->>OrderCtrl: Item record (or empty)
            end

            alt Item is unavailable or missing
                OrderCtrl-->>Student: 200 OK (Renders checkout-error.html: "An item is unavailable.")
            else All items verified
                Note over OrderCtrl: Authoritative server-side summation of order total
                
                Note over OrderCtrl,DB: Transaction Boundary (@Transactional)
                OrderCtrl->>DB: INSERT INTO orders (user_id, total_amount, status) VALUES (?, ?, 'PLACED') (KeyHolder)
                DB-->>OrderCtrl: Generated orderId (e.g. 15)
                
                loop For each item
                    OrderCtrl->>DB: INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity) VALUES (15, ?, ?, ?, ?)
                end
                
                OrderCtrl-->>Student: 200 OK (Renders checkout-bill.html with orderId=15, clearCart=true)
            end
        end
    end
```

---

### 4.5 Payment Processing and Order Confirmation

Details the multi-step simulated payment sequence: bill generation with pessimistic locking, simulated gateway payment, and final receipt rendering.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant PayCtrl as PaymentController
    participant DB as MySQL Database

    Note over Student,PayCtrl: STEP 1: Generate Payment Bill (Authenticated)
    Student->>PayCtrl: POST /payment/bill (orderId=15)
    PayCtrl->>DB: SELECT id, user_id, payment_id, transaction_id FROM orders WHERE id = 15 AND user_id = ? FOR UPDATE
    DB-->>PayCtrl: order record
    
    PayCtrl->>DB: SELECT SUM(unit_price * quantity) FROM order_items WHERE order_id = 15
    DB-->>PayCtrl: calculated total (e.g. 40.00)

    alt Order already has transaction_id (Idempotent replay)
        PayCtrl->>DB: SELECT amount, status FROM payments WHERE transaction_id = ?
        DB-->>PayCtrl: existing payment
        PayCtrl-->>Student: 200 OK (Renders payment-bill.html with existing transaction)
    else New transaction
        Note over PayCtrl: Generate cryptographic 256-bit ID ("CB-...")
        PayCtrl->>DB: INSERT INTO payments (transaction_id, user_id, amount, status) VALUES ('CB-...', ?, 40.00, 'PENDING')
        PayCtrl->>DB: UPDATE orders SET payment_id = ?, transaction_id = 'CB-...', total_amount = 40.00 WHERE id = 15
        PayCtrl-->>Student: 200 OK (Renders payment-bill.html with transactionId)
    end

    Note over Student,PayCtrl: STEP 2: Simulated Payment Execution (Unauthenticated Callback)
    Student->>PayCtrl: POST /payment/pay (transactionId='CB-...')
    PayCtrl->>DB: UPDATE payments SET status = 'PAID', paid_at = CURRENT_TIMESTAMP WHERE transaction_id = 'CB-...' AND status = 'PENDING'
    
    alt Payment already processed or not found
        DB-->>PayCtrl: 0 rows updated
        PayCtrl-->>Student: 200 OK (Renders checkout-error.html)
    else Status transitioned to PAID
        DB-->>PayCtrl: 1 row updated
        PayCtrl->>DB: SELECT o.id, p.transaction_id FROM payments p JOIN orders o ON o.payment_id = p.id WHERE p.transaction_id = 'CB-...' AND p.status = 'PAID'
        DB-->>PayCtrl: orderId=15, transactionId='CB-...'
        PayCtrl-->>Student: 200 OK (Renders payment-redirect-confirmation.html)
    end

    Note over Student,PayCtrl: STEP 3: Order Confirmation Receipt Display
    Student->>PayCtrl: POST /order/confirmation (orderId=15, transactionId='CB-...')
    PayCtrl->>DB: SELECT o.id, o.total_amount, p.transaction_id, p.status, p.paid_at FROM orders o JOIN payments p ON p.id = o.payment_id WHERE o.id = 15 AND o.transaction_id = 'CB-...' AND p.transaction_id = 'CB-...' AND p.status = 'PAID'
    DB-->>PayCtrl: verified order row
    PayCtrl-->>Student: 200 OK (Renders order-confirmation.html receipt)
```

---

### 4.6 Student Order History

Shows an authenticated student retrieving past orders, linking payment status, and populating line item snapshots.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant HistCtrl as OrderHistoryController
    participant DB as MySQL Database

    Student->>HistCtrl: GET /orders
    
    alt Unauthenticated session
        HistCtrl-->>Student: 302 Redirect to /auth/login?returnUrl=/orders
    else Authenticated
        HistCtrl->>DB: SELECT o.id, o.total_amount, o.status, COALESCE(p.status, 'NOT INITIATED') AS payment_status, o.transaction_id, o.created_at FROM orders o LEFT JOIN payments p ON p.id = o.payment_id WHERE o.user_id = ? ORDER BY o.created_at DESC, o.id DESC
        DB-->>HistCtrl: List of orders

        loop For each order
            HistCtrl->>DB: SELECT product_name, unit_price, quantity FROM order_items WHERE order_id = ? ORDER BY id
            DB-->>HistCtrl: List of OrderLine items
        end

        HistCtrl-->>Student: 200 OK (Renders orders.html with aggregated order cards)
    end
```

---

### 4.7 Profile Management

Traces profile inspection, name/roll number updates, and password changes with session token re-issuance.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant Ctrl as WebsiteProfileController
    participant Auth as AuthService
    participant Token as ApiTokenService
    participant DB as MySQL Database
    participant Session as HttpSession

    Note over Student,Ctrl: Sub-Flow A: Update Full Name
    Student->>Ctrl: POST /profile/change/name {"fullName": "Jane Doe"}
    Ctrl->>Token: findUserIdByToken(session.apiToken)
    Token-->>Ctrl: userId
    Ctrl->>Auth: updateFullName(userId, "Jane Doe")
    Auth->>DB: UPDATE users SET full_name = ? WHERE id = ?
    Ctrl->>Session: setAttribute("fullName", "Jane Doe")
    Ctrl-->>Student: 200 OK {"message": "Name updated successfully.", "fullName": "Jane Doe"}

    Note over Student,Ctrl: Sub-Flow B: Update Roll Number
    Student->>Ctrl: POST /profile/change/roll-no {"rollNumber": "2025109999"}
    Ctrl->>Auth: updateRollNumber(userId, "2025109999")
    Auth->>DB: UPDATE users SET roll_number = ? WHERE id = ?
    
    alt Roll number already taken by another account
        DB-->>Auth: DataIntegrityViolationException (UNIQUE constraint)
        Auth-->>Ctrl: DataIntegrityViolationException
        Ctrl-->>Student: 409 Conflict {"message": "That roll number is already registered to another account."}
    else Success
        Ctrl->>Session: setAttribute("rollNumber", "2025109999")
        Ctrl-->>Student: 200 OK {"message": "Roll number updated successfully."}
    end

    Note over Student,Ctrl: Sub-Flow C: Change Password & Token Cascade
    Student->>Ctrl: POST /profile/change-password {currentPassword, newPassword}
    Ctrl->>Auth: changePassword(userId, currentPassword, newPassword)
    Auth->>DB: SELECT password_hash FROM users WHERE id = ?
    Auth->>Auth: passwordEncoder.matches(currentPassword, hash)
    Auth->>DB: UPDATE users SET password_hash = ? WHERE id = ?
    
    Note over Auth,DB: Security Cascade: Invalidate all existing API tokens
    Auth->>DB: UPDATE api_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL
    
    Note over Ctrl,Session: Token Re-Issuance: Keep active browser session logged in
    Ctrl->>Token: generateToken(userId)
    Token-->>Ctrl: newToken
    Ctrl->>Session: setAttribute("apiToken", newToken)
    Ctrl-->>Student: 200 OK {"message": "Password changed successfully."}
```

---

### 4.8 Password Recovery

Illustrates the self-service password recovery flow: anti-enumeration reset request, SMTP dispatch, token validation, and password reset with token revocation.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant ResetCtrl as WebsitePasswordResetController
    participant PageCtrl as PageController
    participant Auth as AuthService
    participant Mail as JavaMailSender
    participant DB as MySQL Database

    Note over Student,ResetCtrl: Phase 1: Request Reset Link
    Student->>ResetCtrl: POST /auth/forgot-password {email: "student@college.edu"}
    ResetCtrl->>Auth: requestPasswordReset("student@college.edu")
    Auth->>DB: SELECT id, email FROM users WHERE email = ?
    
    alt Account does not exist
        DB-->>Auth: Empty result
        Note over Auth: Returns silently without error (Anti-Enumeration Defense)
    else Account exists
        Auth->>DB: UPDATE password_reset_tokens SET used_at = NOW() WHERE user_id = ? AND used_at IS NULL
        Auth->>DB: INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, SHA-256, NOW + 30m)
        Auth->>Mail: send(resetEmailWithRawToken)
    end
    ResetCtrl-->>Student: 200 OK {"message": "If an account exists for that email, a password-reset link will be sent."}

    Note over Student,PageCtrl: Phase 2: Open Reset Form
    Student->>PageCtrl: GET /auth/reset-password?token=<RAW_TOKEN>
    PageCtrl->>Auth: isPasswordResetTokenValid(token)
    Auth->>DB: SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NULL AND expires_at > NOW
    DB-->>Auth: count (1 or 0)
    PageCtrl-->>Student: 200 OK (Renders reset-password.html, toggles invalidLink prompt)

    Note over Student,ResetCtrl: Phase 3: Execute Password Reset
    Student->>ResetCtrl: POST /auth/reset-password {token: "<RAW_TOKEN>", newPassword: "newPassword123"}
    ResetCtrl->>Auth: resetPassword(token, newPassword)
    Auth->>DB: SELECT user_id FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NULL AND expires_at > NOW FOR UPDATE
    
    alt Token expired, invalid, or already consumed
        DB-->>Auth: Empty result
        Auth-->>ResetCtrl: false
        ResetCtrl-->>Student: 400 Bad Request {"message": "The reset link is invalid, expired, or has already been used."}
    else Valid token locked
        Auth->>DB: UPDATE password_reset_tokens SET used_at = NOW() WHERE token_hash = ?
        Auth->>DB: UPDATE users SET password_hash = ? WHERE id = ?
        Auth->>DB: UPDATE api_tokens SET revoked_at = NOW() WHERE user_id = ? (Revoke active sessions)
        Auth->>DB: UPDATE password_reset_tokens SET used_at = NOW() WHERE user_id = ? (Revoke sibling reset tokens)
        Auth-->>ResetCtrl: true
        ResetCtrl-->>Student: 200 OK {"message": "Password reset successfully.", "redirectUrl": "/auth/login"}
    end
```

---

### 4.9 Administrator Authentication and Menu Management

Details administrator login using constant-time credential comparison, in-memory session mapping, cookie issuance, and menu CRUD operations.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Ctrl as AdminController
    participant Map as ConcurrentHashMap (Admin Sessions)
    participant DB as MySQL Database

    Note over Admin,Ctrl: Phase 1: Administrator Login
    Admin->>Ctrl: POST /admin/api/login {username, password}
    Note over Ctrl: Constant-time byte comparison via MessageDigest.isEqual
    
    alt Invalid credentials
        Ctrl-->>Admin: 401 Unauthorized {"message": "Invalid username or password."}
    else Valid credentials
        Note over Ctrl: Generate 256-bit cryptographically secure session ID
        Ctrl->>Map: put(sessionId, AdminSession(username, now + 8 hours))
        Ctrl-->>Admin: 200 OK {"message": "Login successful."} (Sets ADMINSESSIONID cookie, HttpOnly, SameSite=Strict, Path=/admin)
    end

    Note over Admin,Ctrl: Phase 2: Menu Inventory Management (Authenticated via Cookie)
    Admin->>Ctrl: GET /admin/api/items (Cookie: ADMINSESSIONID)
    Ctrl->>Map: get(sessionId)
    Ctrl->>DB: SELECT id, name, description, category, price, image_link, available FROM items ORDER BY category, name
    DB-->>Ctrl: List<MenuItem>
    Ctrl-->>Admin: 200 OK [JSON items list]

    Admin->>Ctrl: POST /admin/api/items {id: "paneer-wrap", name: "Paneer Wrap", price: 70.00, ...}
    Ctrl->>DB: INSERT INTO items (id, name, description, category, price, image_link, available) VALUES (?, ?, ?, ?, ?, ?, ?)
    
    alt Duplicate item ID
        DB-->>Ctrl: DuplicateKeyException
        Ctrl-->>Admin: 409 Conflict {"message": "An item with that ID already exists."}
    else Successful creation
        DB-->>Ctrl: 1 row affected
        Ctrl-->>Admin: 201 Created {"message": "Item created successfully."}
    end

    Admin->>Ctrl: PUT /admin/api/items/paneer-wrap {id: "paneer-roll", name: "Paneer Roll", price: 75.00, ...}
    Ctrl->>DB: UPDATE items SET id = 'paneer-roll', name = 'Paneer Roll', price = 75.00 WHERE id = 'paneer-wrap'
    Ctrl-->>Admin: 200 OK {"message": "Item updated successfully."}

    Admin->>Ctrl: DELETE /admin/api/items/paneer-roll
    Ctrl->>DB: DELETE FROM items WHERE id = 'paneer-roll'
    Ctrl-->>Admin: 200 OK {"message": "Item deleted successfully."}

    Note over Admin,Ctrl: Phase 3: Administrator Logout
    Admin->>Ctrl: POST /admin/api/logout (Cookie: ADMINSESSIONID)
    Ctrl->>Map: remove(sessionId)
    Ctrl-->>Admin: 200 OK (Sets ADMINSESSIONID cookie Max-Age=0)
```

---

### 4.10 Administrator Order Management

Illustrates how canteen kitchen staff inspect paid orders and advance orders through the preparation state machine.

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant OrderCtrl as AdminOrderController
    participant AdminCtrl as AdminController
    participant DB as MySQL Database

    Note over Admin,OrderCtrl: Sub-Flow A: View Paid Orders Queue
    Admin->>OrderCtrl: GET /admin/orders (Cookie: ADMINSESSIONID)
    OrderCtrl->>AdminCtrl: requireAdminAccess(request)
    
    alt Unauthenticated / Expired Cookie
        AdminCtrl-->>OrderCtrl: ResponseStatusException(401 Unauthorized)
        OrderCtrl-->>Admin: 401 Unauthorized
    else Valid Admin Session
        Note over OrderCtrl,DB: Correlated subquery collapses line items via GROUP_CONCAT in 1 query
        OrderCtrl->>DB: SELECT o.id, u.full_name, u.roll_number, o.total_amount, o.status, p.transaction_id, o.created_at, (SELECT GROUP_CONCAT(...) FROM order_items oi WHERE oi.order_id = o.id) AS items FROM orders o JOIN users u ON u.id = o.user_id JOIN payments p ON p.id = o.payment_id WHERE p.status = 'PAID' ORDER BY CASE o.status WHEN 'PLACED' THEN 1 WHEN 'PREPARING' THEN 2 WHEN 'READY' THEN 3 WHEN 'COMPLETED' THEN 4 WHEN 'CANCELLED' THEN 5 END, o.created_at ASC
        DB-->>OrderCtrl: List of paid orders
        OrderCtrl-->>Admin: 200 OK (Renders admin-orders.html kitchen dashboard)
    end

    Note over Admin,OrderCtrl: Sub-Flow B: Advance Order Status
    Admin->>OrderCtrl: POST /admin/orders/15/status (status="PREPARING")
    OrderCtrl->>AdminCtrl: requireAdminAccess(request)
    
    alt Status transition not allowed in state machine
        Note over OrderCtrl: SQL atomic condition: Checks (o.status='PLACED' AND ? IN ('PREPARING', 'CANCELLED')) OR (o.status='PREPARING' AND ? IN ('READY', 'CANCELLED')) OR (o.status='READY' AND ? IN ('COMPLETED', 'CANCELLED'))
        OrderCtrl->>DB: UPDATE orders o JOIN payments p ON p.id = o.payment_id SET o.status = 'PREPARING' WHERE o.id = 15 AND p.status = 'PAID' AND (...)
        DB-->>OrderCtrl: 0 rows affected (Invalid transition or unpaid order)
        OrderCtrl-->>Admin: 302 Redirect to /admin/orders (Flash error: "Order not found, not paid, or the status transition is not allowed.")
    else Transition valid
        DB-->>OrderCtrl: 1 row affected
        OrderCtrl-->>Admin: 302 Redirect to /admin/orders (Flash success: "Order #15 updated to PREPARING.")
    end
```

---

## 5. Design Summary and Limitations

### Design Summary
CampusBite implements a pragmatic, clean monolithic design that couples Spring Boot 3.5.0, Thymeleaf, and MySQL 8.0:
- **Direct JDBC Persistence:** Persists data using Spring's `JdbcTemplate` with raw SQL queries and transactional boundaries (`@Transactional`), deliberately avoiding ORM configuration, lazy-loading pitfalls, and Hibernate cache overhead.
- **Architectural Coupling:** Controller classes actively manage business transactions and data querying directly for orders, payments, and admin queues. Dedicated services (`AuthService`, `ApiTokenService`, `SessionRefreshService`) encapsulate cross-cutting identity and security logic.
- **Multi-Tier Identity Architecture:** Distinct client channels are segregated at the architectural level: student web sessions rely on standard servlet sessions validated against database tokens; mobile clients use stateless Bearer tokens with SHA-256 database storage; and administrators use an independent cookie linked to an in-memory session registry.
- **Transactional & Concurrency Guarantees:** Concurrency race conditions are actively managed at critical points using pessimistic row locking (`SELECT ... FOR UPDATE`) during payment bill generation and password reset redemption.

### Implementation Limitations
Based on an analysis of the codebase, the following limitations are present in the actual implementation:

1. **Simulated Payment Processing:** The payment workflow simulates external gateway processing via internal status updates (`PENDING` to `PAID`). There is no integration with an external payment gateway API, payment webhook signature validation, or real merchant settlement.
2. **In-Memory Administrator Session Store:** Administrator sessions reside in a `ConcurrentHashMap` inside `AdminController`. Sessions do not survive server restarts, cannot be shared across multiple application instances, and require manual opportunistic garbage collection.
3. **Static Administrator Credentials:** Admin credentials (`app.admin.username` and `app.admin.password`) are defined as static environment variables/properties rather than as database-backed records with independent password hashing.
4. **Client-Side Shopping Cart Persistence:** Shopping cart items are stored entirely within browser `localStorage`. If a student switches devices or browsers before checkout, cart contents do not persist.
5. **Binary Inventory Availability:** The `items` table tracks stock solely through a boolean flag (`available = TRUE/FALSE`). The system lacks inventory quantity tracking, stock decrementing, or automated out-of-stock triggers when high demand occurs.
6. **Single-Canteen Tenant Architecture:** The schema and controllers are architected for a single campus canteen; there is no multi-vendor or multi-canteen partitioning in the database schema.
