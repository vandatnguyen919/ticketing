# Email/password authentication and password reset API

## Goal

Add traditional email and password registration and login next to the existing
GitHub and Google OAuth login, plus a three-step password reset flow: an
asynchronous reset request that emails a one-time code, a verify step that
returns a short-lived reset ticket, and a confirm step that stores the new
password. Every route lives under `/api/v1/auth/...` and follows the existing
API versioning.

This spec covers the backend API. The React screens for register, forgot
password, and reset password are a follow-up.

## Current behavior

- GitHub and Google OAuth authorization-code login is the only sign-in path.
- `POST /api/v1/auth/exchange-session` issues the backend JWT in an HttpOnly
  cookie; JavaScript never reads the JWT.
- The `users` Spring Data JDBC aggregate holds `id`, `email`, `display_name`,
  `provider` (`GITHUB` or `GOOGLE`), and `provider_id`, keyed by
  `(provider, provider_id)` and managed by Flyway.
- There is no password column, no local credential check, no cache abstraction
  in use anywhere, and no email sending.
- `AuthController` maps `/api/{version}/auth` with Spring's native version
  indicator, so the externally visible routes are `/api/v1/auth/...`.
- Unsafe requests are CSRF protected through `CookieCsrfTokenRepository` and the
  `X-XSRF-TOKEN` header.

## Scope

- Email/password registration, login, and password reset are now in scope for
  the backend API.
- The PRODUCT.md non-goal about no email delivery covers ticket delivery. This
  feature sends transactional authentication email only: password reset codes.
  Ticket emails, QR codes, and PDF tickets stay out of scope.
- Email/password accounts join the existing `users` aggregate. Confirmed with
  Dan: add a nullable `password_hash` column and an `EMAIL` provider value.

## Requirements

### REQ-001: Versioned routes

- Add register, login, and password-reset operations to the versioned auth
  surface at `/api/v1/auth/...`.
- Reuse the existing native version indicator style of `AuthController`. Do not
  introduce a second versioning scheme or hard-code `/v1` in new mappings.
- `SecurityConfig` permits the new public POST routes while CSRF protection
  stays enabled for them.

### REQ-002: Registration

- `POST /api/v1/auth/register` accepts email, password, and display name.
- Validate and normalize input: email must be a valid address and is stored
  lowercased, password is 10 to 72 characters so the whole secret reaches
  bcrypt, display name is 1 to 100 characters.
- Create a `users` row with `provider = 'EMAIL'`, `provider_id` set to the
  normalized email, and `password_hash` set to a `BCryptPasswordEncoder` hash.
- A duplicate email returns 409. This is the one endpoint that reveals account
  existence, because signup needs that feedback.
- Success returns 201 Created with the safe profile body and sets the same
  HttpOnly JWT cookie that OAuth exchange sets, so the user is signed in
  immediately. The JWT is never in the response body.

### REQ-003: Login

- `POST /api/v1/auth/login` accepts email and password.
- Authenticate through Spring Security's `AuthenticationManager` with a
  `DaoAuthenticationProvider` backed by a `UserDetailsService` over the `users`
  table and a `BCryptPasswordEncoder`.
- Success returns 200 with the safe profile body and the HttpOnly JWT cookie,
  identical in shape and flags to the OAuth exchange response.
- Any failure returns 401 with one generic message. Unknown email, wrong
  password, and OAuth-only accounts without a password all look the same. When
  the account does not exist, still run a dummy bcrypt compare so response time
  does not reveal existence.

### REQ-004: Password reset request

- `POST /api/v1/auth/password-reset/request` accepts an email address.
- Always return 202 Accepted with one generic body, whether or not an account
  exists. This prevents account enumeration.
- When a local credential account exists, generate a 6 digit numeric code from a
  secure random source, store it through the cache abstraction (REQ-007), and
  dispatch the email asynchronously (REQ-008).
- One code per account at a time. A new request inside a 60 second cooldown
  returns the same 202 body and does not generate or send anything. A request
  outside the cooldown replaces any older code.

### REQ-005: OTP verification

- `POST /api/v1/auth/password-reset/verify` accepts email and the code string.
- On match, issue a single-use opaque reset ticket: 32 random bytes, base64url
  encoded, valid 5 minutes. Return 200 with the ticket and its remaining
  lifetime. The ticket is the "validated state" carried into the confirm step.
- Compare against the stored bcrypt hash of the code, not a plaintext value.
- Failures return 400 with a stable error code: `invalid_otp`, `otp_expired`,
  or `otp_locked`.
- After 5 failed attempts the code is invalidated and the user must request a
  new one. Attempt counting lives in the same cache entry.

### REQ-006: Password reset confirmation

- `POST /api/v1/auth/password-reset/confirm` accepts the reset ticket and the
  new password.
- The new password follows the same rules as registration.
- Success stores a new `BCryptPasswordEncoder` hash on the `users` row, evicts
  the code and the ticket from the cache, and returns 204 No Content.
- The ticket is single use. A second confirm with the same ticket returns 400
  with `reset_ticket_invalid`. An expired or unknown ticket returns the same
  error.
- Confirm does not sign the user in. The client routes to login afterwards.

### REQ-007: OTP and ticket storage through the Spring Cache abstraction

- Codes and reset tickets are stored only through Spring's Cache API:
  `CacheManager` plus `@Cacheable`, `@CachePut`, and `@CacheEvict` in a dedicated
  `auth.otp` service.
- Do not use an in-memory `Map`, a session attribute, or a Postgres table for
  this state. The cache provider must be swappable without touching service
  code.
- Cache names: `auth-password-reset-otp` and `auth-password-reset-ticket`.
- The code cache entry is a record of `codeHash`, `createdAt`, `attempts`, and
  purpose. The ticket cache entry is a record of `accountEmail` and `createdAt`.
  Cache keys and values hold hashes only, never a usable plaintext code or
  ticket, so a cache dump is not a credential.
- Time to live is expressed in cache configuration, not in service timers:
  Caffeine `expireAfterWrite` for local runs, a cache TTL setting when a Redis
  `CacheManager` replaces it. Moving to Redis changes only the `CacheManager`
  bean and cache TTL configuration.
- Enable caching with `@EnableCaching`. Add `spring-boot-starter-cache` and
  Caffeine as the current provider.

### REQ-008: Asynchronous email dispatch

- Define an `AuthEmailService` port and dispatch reset emails with `@Async`
  behind `@EnableAsync`, so the request never waits on an SMTP round trip.
- The default implementation uses `spring-boot-starter-mail` and
  `JavaMailSender` behind the same port. Local runs point it at Mailpit, so the
  real SMTP path is exercised instead of a fake one.
- Mailpit runs in Docker Compose: SMTP on `127.0.0.1:1025` and a web UI on
  `http://127.0.0.1:8025` where captured reset emails can be read during
  development and manual checks. It captures mail and never sends it anywhere.
- Unit tests use a capturing test double and need no Docker.
- Production SMTP settings come from the environment or Vault and stay an open
  choice. Mailpit is a local test target, not a production mail service.
- A dispatch failure is logged and never changes the API response. The request
  still returns 202.

### REQ-009: Password hashing baseline

- Expose one `PasswordEncoder` bean of type `BCryptPasswordEncoder` with default
  strength 10. Registration, login, and confirm all use it.
- Plaintext passwords never reach logs, responses, or cache entries.

### REQ-010: Security rules

- Public routes: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, and
  `POST /api/v1/auth/password-reset/**`. Everything else under `/api/v1` keeps
  its current rules.
- CSRF protection stays on for these public unsafe methods. The client fetches
  `GET /api/v1/auth/csrf` and sends `X-XSRF-TOKEN` as it does today.
- Error responses avoid account enumeration. Only registration reports that an
  email is taken.

## API contracts

Error bodies for the new endpoints are JSON:
`{ "error": "<stable_code>", "message": "<safe text>" }`.
Successful login and registration reuse the existing `AuthResponse` shape:
`{ "user": { "email": "...", "name": "...", "provider": "email" } }`.
The JWT cookie is named `ticketing-token`, `HttpOnly`, `SameSite=Lax`,
`Path=/api`, `Secure` in production, matching the OAuth exchange cookie.

### POST /api/v1/auth/register

Request:

```json
{
  "email": "dana@example.com",
  "password": "long-enough-password",
  "displayName": "Dana"
}
```

| Status | Body | Notes |
| --- | --- | --- |
| 201 Created | `AuthResponse` | Sets the JWT cookie. Auto sign-in. |
| 400 Bad Request | `validation_error` with field messages | Bad email, short or long password, empty display name. |
| 409 Conflict | `email_taken` | Email already registered. |
| 403 Forbidden | CSRF failure | Missing or invalid `X-XSRF-TOKEN`. |

### POST /api/v1/auth/login

Request:

```json
{ "email": "dana@example.com", "password": "long-enough-password" }
```

| Status | Body | Notes |
| --- | --- | --- |
| 200 OK | `AuthResponse` | Sets the JWT cookie. |
| 400 Bad Request | `validation_error` | Missing or malformed fields. |
| 401 Unauthorized | `invalid_credentials` | One generic message for every failure cause. |
| 403 Forbidden | CSRF failure | Missing or invalid `X-XSRF-TOKEN`. |

### POST /api/v1/auth/password-reset/request

Request:

```json
{ "email": "dana@example.com" }
```

| Status | Body | Notes |
| --- | --- | --- |
| 202 Accepted | `{ "status": "RESET_REQUEST_ACCEPTED", "message": "If an account exists for this email, a reset code has been sent." }` | Always, existing account or not. |
| 400 Bad Request | `validation_error` | Malformed email only. |
| 403 Forbidden | CSRF failure | Missing or invalid `X-XSRF-TOKEN`. |

### POST /api/v1/auth/password-reset/verify

Request:

```json
{ "email": "dana@example.com", "code": "481920" }
```

| Status | Body | Notes |
| --- | --- | --- |
| 200 OK | `{ "resetTicket": "<opaque>", "expiresInSeconds": 300 }` | Single-use ticket for the confirm step. |
| 400 Bad Request | `invalid_otp` | Wrong code. |
| 400 Bad Request | `otp_expired` | Code past its 10 minute TTL. |
| 400 Bad Request | `otp_locked` | 5 failed attempts; a new request is required. |
| 403 Forbidden | CSRF failure | Missing or invalid `X-XSRF-TOKEN`. |

### POST /api/v1/auth/password-reset/confirm

Request:

```json
{ "resetTicket": "<opaque>", "newPassword": "a-brand-new-password" }
```

| Status | Body | Notes |
| --- | --- | --- |
| 204 No Content | empty | Password updated. Client routes to login. |
| 400 Bad Request | `validation_error` | New password fails the password rules. |
| 400 Bad Request | `reset_ticket_invalid` | Unknown, expired, or already used ticket. |
| 403 Forbidden | CSRF failure | Missing or invalid `X-XSRF-TOKEN`. |

## Internal packages

New code lives under `com.example.ticketing.auth`, so the feature reads as one
unit. Existing OAuth classes stay where they are; moving them is not part of
this work.

| Package | Contents |
| --- | --- |
| `auth.dto` | `RegisterRequest`, `LoginRequest`, `PasswordResetRequest`, `PasswordResetVerifyRequest`, `PasswordResetConfirmRequest`, `PasswordResetRequestResponse`, `PasswordResetVerifyResponse`, `ApiError` |
| `auth.service` | `EmailPasswordUserService` for registration and user loading, `PasswordResetService` for request, verify, and confirm orchestration |
| `auth.otp` | `OtpCacheService` and `ResetTicketStore` built on `CacheManager` with `@Cacheable` / `@CachePut` / `@CacheEvict`, plus `OtpRecord` and `ResetTicketRecord` |
| `auth.mail` | `AuthEmailService` port, `AsyncAuthEmailService` with `@Async`, `LoggingAuthEmailService` for local and test runs, `MailAuthEmailService` using `JavaMailSender` for production |
| `auth.security` | `LocalUserDetailsService` and the `DaoAuthenticationProvider` wiring |
| `auth.web` | `EmailPasswordAuthController` and `PasswordResetController` |

Existing packages change in place:

- `config` - `SecurityConfig` permits the new public routes and exposes the
  `BCryptPasswordEncoder` and authentication provider beans.
- `model` - `User` gains a nullable `passwordHash` field mapped to
  `password_hash`, and `OAuthProvider` gains `EMAIL`.
- `repository` - `UserRepository` gains lookup by provider and email for the
  `EMAIL` provider.
- `service` - `JwtService` is reused unchanged to mint the login cookie.
- `model.AuthResponse` and `model.UserProfile` are reused as the login and
  registration response shape.

## Data model change

New Flyway migration `V3__add_local_password_credentials.sql`:

- `ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);` Nullable, so
  existing OAuth rows are untouched and mean "no local password".
- Replace the provider CHECK constraint so it also allows `'EMAIL'`.
- `EMAIL` rows set `provider_id` to the normalized email, which keeps the
  existing "keyed by provider and stable subject" invariant and lets the
  existing `UNIQUE (provider, provider_id)` constraint enforce one account per
  email.
- Forward only, no destructive change.

## Build and configuration changes

- `backend/build.gradle` - add `spring-boot-starter-cache`, Caffeine, and
  `spring-boot-starter-mail`.
- `backend/src/main/resources/application.yml` - enable caching, set the
  Caffeine cache TTL specs for the two reset caches, and add settings for code
  length, code TTL, ticket TTL, max attempts, and the request cooldown, for
  example `app.auth.otp.ttl: 10m`, `app.auth.reset-ticket.ttl: 5m`,
  `app.auth.otp.max-attempts: 5`, `app.auth.reset-request-cooldown: 60s`. Local
  mail points at Mailpit: `spring.mail.host: 127.0.0.1`, `spring.mail.port:
  1025`, no auth and no TLS. Production mail settings come from the environment
  or Vault.
- `docker-compose.yml` - the Mailpit service is already added, pinned to
  `axllent/mailpit:v1.31.3` with SMTP on `127.0.0.1:1025` and the UI on
  `127.0.0.1:8025`, both loopback only for local development like the Vault UI.
- `config/SecurityConfig.java` - new public route rules and the authentication
  provider wiring described above.

## Decisions made in this spec

- Credentials extend the `users` aggregate with `password_hash` and an `EMAIL`
  provider value. Confirmed with Dan.
- Verify returns a single-use opaque reset ticket in the JSON body. Confirm
  submits that ticket with the new password. It is not a cookie and not a JWT.
- Register signs the user in immediately: 201 with the JWT cookie.
- The reset request always returns 202, existing account or not.
- Confirm returns 204 and does not sign the user in.
- Codes are 6 digits with a 10 minute TTL and 5 attempts. Reset tickets live 5
  minutes and are single use.
- Cache entries store hashes only.
- Only transactional authentication email is sent. Ticket email stays out of
  scope.
- Mailpit is the local and test mail target. It captures reset emails for
  inspection at `http://127.0.0.1:8025` and lets the real `JavaMailSender` path
  run without an external provider or leaked mail. Chosen by Dan. Production
  SMTP is still open.

## Acceptance criteria

- The five endpoints resolve at their `/api/v1/auth/...` paths through the
  existing versioning, and no unversioned duplicate is exposed.
- Registration creates an `EMAIL` account with a bcrypt hash and returns 201
  with the HttpOnly JWT cookie; a duplicate email returns 409.
- Login with correct credentials returns 200 with the cookie. Unknown email,
  wrong password, and OAuth-only accounts all return the same 401 body.
- A password reset request always returns 202 and only sends email for an
  existing local credential account.
- The code and the reset ticket are stored only through Spring's Cache
  abstraction with configured TTLs. No `Map`, session, or table holds them.
- The reset email is dispatched asynchronously and never delays or changes the
  API response.
- Verify returns a single-use ticket; confirm consumes it once, stores a bcrypt
  hash, and clears both cache entries.
- Codes expire after 10 minutes and lock after 5 failed attempts; tickets expire
  after 5 minutes and cannot be reused.
- CSRF protection applies to all five POST routes.
- Existing OAuth login, catalog, and booking behavior is unchanged.

## Verification

- Run `.\verify.ps1 -Check backend`, which runs the Gradle test suite with JDK
  27.
- New focused tests:
  - Controller tests for all five routes covering every status code in the
    tables above, cookie flags on register and login, and the generic 401 and
    202 bodies.
  - `OtpCacheService` tests through the Cache abstraction using a test
    `CacheManager`, covering generate, compare, attempts, lockout, TTL expiry,
    and eviction on confirm.
  - A configuration test that asserts the Caffeine TTL specs are parsed and
    active, which catches TTLs being silently ignored.
  - Service tests for bcrypt hashing, the dummy compare on unknown accounts,
    the request cooldown, and single-use ticket consumption.
  - A mail test double that captures the async dispatch and proves the request
    path does not wait on it.
  - An SMTP integration check that sends through `JavaMailSender` to Mailpit and
    reads the captured message back from the Mailpit API, which is the only
    check that exercises the real mail path.
  - Migration test that existing user rows survive with a NULL `password_hash`
    and that `EMAIL` rows are accepted.
- Manual check against a running backend, Postgres, and Mailpit from Docker
  Compose: run the five endpoints with curl, confirm the reset code shows up in
  the Mailpit UI at `http://127.0.0.1:8025`, confirm expiry, lockout, ticket
  reuse rejection, and that the JWT is only ever in the cookie.

## Open items

- A JWT issued before a password reset stays valid until it expires. This is
  the same unresolved question as server-side logout revocation in the OAuth
  spec. Decide both together, for example with a token version claim.
- Mailpit covers local development and tests only. The production SMTP
  provider, from-address, and reset email template are undecided. The mail port
  keeps this swappable.
- Account linking, for example attaching a Google login to an existing
  `EMAIL` account, is out of scope. Each account has exactly one provider
  value today.
- The React screens for register, forgot password, and reset password are a
  follow-up feature.
