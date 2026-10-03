# Event Ticketing Platform MVP

## Purpose
This project is a solo-developer proof of concept for a decoupled ticketing platform. It demonstrates secure OAuth login, a public event catalog, and a transactional ticket booking flow in a small but realistic architecture.

## Decoupled system
- React + TypeScript + Tailwind frontend
- Spring Boot REST API backend
- Spring Security OAuth2 Client + Resource Server for GitHub and Google sign-in
- Postgres 18 in Docker Compose, managed by Flyway
- Spring Data JDBC for persistence

## First useful result
A user can sign in with GitHub or Google, view available events, and reserve one ticket for an event without overselling inventory.

### Acceptance criteria
- The frontend starts OAuth login and the backend exchanges the provider identity for a backend-issued JWT in an HttpOnly cookie.
- The React client includes credentials and CSRF tokens on protected API requests; JavaScript never reads the JWT.
- GitHub and Google authentication use the OAuth 2.0 Authorization Code flow handled by the backend.
- `GET /api/v1/events` returns a list of events with at least `id`, `title`, `date`, and `remainingTickets`.
- `POST /api/v1/events/{id}/book` requires cookie authentication and reserves exactly one ticket in a single database transaction.
- If the event has available inventory, the booking succeeds and the remaining count decreases by 1.
- If inventory is zero, the API returns a clear failure response instead of overselling.

## Non-goals
Not now, and intentionally out of scope:
- No Stripe, PayPal, or any real payment flow
- No seat maps or venue layouts
- No QR code generation, email delivery, or PDF tickets
- No admin UI for creating events; all seed data comes from Flyway SQL
- No server-side rendering frameworks such as Thymeleaf or JTE

## Decisions
- The backend Gradle build uses the Groovy DSL (`build.gradle` and `settings.gradle`); it matches the requested build-script language, with Kotlin DSL's stronger static IDE support traded off for Groovy syntax.
- API authentication uses a backend-issued JWT in an HttpOnly, Secure-in-production, SameSite=Lax cookie. JavaScript never reads or persists the JWT.
- The backend handles provider authorization-code callbacks; the temporary OAuth session is exchanged through a CSRF-protected endpoint for a JWT cookie and safe user profile.
- OAuth provider client credentials are stored in Vault and loaded by the backend at startup, not committed configuration.
- The frontend includes credentials on API calls and sends a CSRF token on unsafe methods by copying the readable `XSRF-TOKEN` cookie value into `X-XSRF-TOKEN` at request time. The client never caches tokens and never retries a rejected unsafe request; `GET /api/v1/auth/csrf` only bootstraps the cookie and returns no token value. Users sign in again after JWT expiry because refresh tokens are not in scope.
- The JWT signing key is stored in Vault as `security.jwt-secret`; no default signing key is configured.
- REST routes use Spring MVC native API version mappings under `/api/v1`, with `v1` as the default version.
- OAuth user identity is persisted as a Spring Data JDBC aggregate keyed by provider and stable provider subject, not email.
- Protected API requests without a valid credential return 401; sold-out booking attempts return 409.
- The catalog is public and only booking requires authentication.
- Both GitHub and Google are supported in the first version.
- Repeated booking requests are allowed as long as inventory remains, with each request reserving one ticket.
- Inventory is tracked per event and reduced atomically inside a single transaction.
- Events are seeded via Flyway SQL instead of admin tooling.
- Local OAuth credentials and the JWT signing key are read from a persistent single-node HashiCorp Vault KV v2 instance running in Docker Compose; the backend uses a path-scoped read-only token.
- The Compose Vault UI and API bind to loopback over HTTP for local development only. This setup is not suitable for production or remote exposure.
- Email/password registration, login, and password reset are in scope beside OAuth. Only transactional authentication email (password reset codes) is sent, so the no-email-delivery non-goal keeps covering ticket delivery. Passwords are hashed with Spring Security's `BCryptPasswordEncoder`, and reset codes live in Spring's Cache abstraction so the cache provider can move to Redis without service changes.
- Mailpit runs in Docker Compose as the local and test SMTP target for authentication email, with SMTP on `127.0.0.1:1025` and its UI on `127.0.0.1:8025`. It captures mail instead of delivering it, so the real `JavaMailSender` path runs with no external provider and no real addresses reach anyone; production SMTP stays a separate open choice.

## Unresolved questions to confirm in the next build step
- What exact event payload shape should be kept in the database and returned by the API?
- Should the booking endpoint accept `eventId` only, or also include a user-facing ticket quantity?

## Current state
The event catalog and single-ticket review flow are implemented. Login and signup use separate pages, with GitHub and Google OAuth as the supported authentication methods; email/password authentication and password reset are not available. The backend uses Java 27 and Spring Boot 4.1.1, versioned REST routes, cookie-based JWT authentication, Flyway-managed Spring Data JDBC user aggregates, and CSRF-protected unsafe requests. Vault is configured in Docker Compose with persistent file storage, an enabled local UI, and a read-only backend policy. Vault must be initialized and unsealed, the OAuth and JWT secrets entered at `kv/ticketing`, and a scoped backend token provided through `VAULT_TOKEN` before interactive sign-in can work. Mailpit is added to Docker Compose as the local mail target and is running now. The email/password and password-reset backend API is specified in `.shipit/specs/email-password-auth-and-password-reset.md` and is not implemented yet.
