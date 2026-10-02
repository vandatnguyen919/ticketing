# Versioned OAuth authentication and API

## Goal

Make the React client and Spring REST API use a secure, explicit authentication
contract while establishing `/api/v1` as the first supported API version. Upgrade
the backend build from Java 21 and Spring Boot 3 to Java 27 and Spring Boot 4.

## Current behavior

- GitHub and Google OAuth authorization-code login are handled by Spring Security.
- The OAuth success handler loads a verified provider profile, stores it in a
  short-lived server session, and redirects to the React callback route.
- `GET /api/auth/csrf` and `POST /api/auth/exchange-session` exchange that
  session for a backend-signed JWT. The client currently stores the JWT in
  local storage and sends it in an Authorization bearer header.
- `GET /api/auth/me` returns claims from the JWT. The current user persistence
  uses JDBC against `app_users` with `provider`, `email`, and `name`.
- Auth routes use `/api/auth/...`; catalog routes use `/api/events/...`.
- The backend currently targets Java 21 and Spring Boot 3.4.3.

## Requirements

### REQ-001: Backend and framework baseline

- Set the Java toolchain to Java 27 and migrate the Spring Boot plugin and
  managed dependencies to Spring Boot 4.
- Keep the Gradle wrapper compatible with Java 27 and Spring Boot 4.
- Preserve the existing OAuth2 client/resource-server behavior for Google and
  GitHub.

### REQ-002: Native URI-segment API versioning

- Use Spring Boot 4 / Spring MVC's native API versioning and request-mapping
  version indicators. Do not add custom path regex interceptors.
- Expose REST API routes under `/api/v1`, including auth, events, and booking.
- Configure `v1` as the default API version and resolve the version from the
  URI path segment.
- Spring Boot 4.1.1 documents `spring.mvc.apiversion.use.path-segment` as an
  integer URI path-segment index and `spring.mvc.apiversion.default` as the
  string default. For `/api/v1/...`, set the segment index to `1` (`api` is
  index `0`, `v1` is index `1`) and the default to `v1`. The spelling
  `spring.mvc.api-version.use-path-segment=true` is not the supported property;
  tests must verify that the selected Boot configuration resolves `/api/v1`.
- Route security matchers and frontend API requests must use the versioned
  paths. Existing unversioned routes must not accidentally remain the
  documented or frontend-facing API.

### REQ-003: Stateless REST API authentication

- Keep API controllers as JSON REST controllers. Do not add MVC views,
  `ModelAndView`, or template-engine responses.
- Support OAuth2 authorization-code login for Google and GitHub.
- Provide versioned endpoints for CSRF initialization, exchanging the
  successful OAuth login for the application credential, reading the current
  user, discovering supported providers, and logging out.
- Require authentication for current-user and booking operations. Keep the
  event catalog public.
- Return the signed JWT to the browser only as a `Secure`, `HttpOnly`, and
  `SameSite` cookie. Do not include the JWT in a response body or persist it in
  local storage.
- Configure Spring Security's JWT bearer-token resolution to extract the token
  from the named cookie. This is the authentication-token transport, not an
  OAuth provider cookie or a server-side application session.
- Send cookies with frontend API requests and preserve CSRF protection for
  cookie-authenticated unsafe methods, including exchange, booking, and logout.
- Logout must expire the browser's credential cookie. Whether logout also
  invalidates a copied JWT server-side is an open decision below.
- OAuth authorization state may use Spring Security's temporary server-side
  state. It is not an API login session or a reason to make REST controllers
  stateful.

### REQ-004: Persist provider identity as a Spring Data JDBC aggregate

- Persist OAuth users in a `users` table managed by Flyway.
- Map fields as `id` (primary key), `email`, `display_name`, `provider`
  (closed provider enum), and `provider_id`.
- Capture the stable provider subject/id from Google or GitHub; do not identify
  an account using email alone.
- Use Spring Data JDBC aggregate mappings and repository/service boundaries
  rather than ad hoc table names or unmapped persistence assumptions.
- Add a forward-only Flyway migration from existing `app_users` data. Preserve
  existing user identities and avoid destructive table replacement.

### REQ-005: React authentication loop

- Keep OAuth start and callback handling in the React SPA, including explicit
  handling for provider failure and expired/invalid exchange state.
- Call versioned auth endpoints with credentials enabled; JavaScript must not
  read, store, or attach the JWT.
- On sign-in, load/display the current user using the cookie; on logout, call
  the versioned logout endpoint and transition the UI to signed-out state.
- A missing, expired, or invalid credential must result in a signed-out client
  state and a clear unauthorized response for protected API requests.

## API surface to implement

Version the existing surface unless implementation evidence requires a
compatible correction:

| Method | Versioned path | Access |
| --- | --- | --- |
| GET | `/api/v1/auth/csrf` | Public |
| GET | `/api/v1/auth/providers` | Public |
| POST | `/api/v1/auth/exchange-session` | OAuth callback state + CSRF |
| GET | `/api/v1/auth/me` | Authenticated |
| POST | `/api/v1/auth/logout` | CSRF-protected; expires credential cookie |
| GET | `/api/v1/events` | Public |
| POST | `/api/v1/events/{eventId}/book` | Authenticated |

The Spring request mappings must carry native version indicators. The table
describes externally visible URLs, not a requirement to hard-code `/v1` into
each controller mapping if Boot's version-aware mapping produces those URLs.

## Token extraction decision

**Confirmed:** Use an HttpOnly, Secure, SameSite cookie for the backend JWT.
The JWT must not be returned to JavaScript in the exchange JSON body, stored in
local storage, or sent by the frontend in an Authorization header. Spring
Security must resolve the JWT from the cookie. Frontend requests must include
credentials, and unsafe requests must send the CSRF header obtained from the
versioned CSRF endpoint.

Implementation defaults to use unless deployment constraints contradict them:

- Use a dedicated cookie name such as `ticketing-token`, scoped to `/api`.
- Use `SameSite=Lax` for same-site frontend/API deployment. If deployment is
  cross-site, use `SameSite=None; Secure` and retain strict explicit CORS
  origins.
- Set `Secure` in production and allow non-Secure cookies only in local
  HTTP development.
- Match cookie expiry to the JWT expiry. Do not introduce refresh tokens in
  this feature.

## File paths expected to change or be added

### Backend build and configuration

- `backend/build.gradle` — Spring Boot 4 plugin/dependency baseline and
  Java 27 toolchain.
- `backend/gradle/wrapper/gradle-wrapper.properties` — wrapper version compatible
  with the selected Java/Boot release.
- `backend/src/main/resources/application.yml` — native path-segment API version
  configuration, default version `v1`, and removal of insecure credential
  fallbacks; retain secrets as required environment configuration.
- `backend/src/main/java/com/example/ticketing/config/SecurityConfig.java` —
  versioned access rules, CSRF, OAuth2, and stateless bearer API configuration.
- Add a cookie JWT resolver/security configuration under
  `backend/src/main/java/com/example/ticketing/config/` and wire it into the
  resource-server bearer-token flow.

### Backend auth, persistence, and versioned controllers

- `backend/src/main/java/com/example/ticketing/controller/AuthController.java` —
  native request-mapping version indicators on auth API operations and a
  cookie-clearing logout endpoint.
- `backend/src/main/java/com/example/ticketing/controller/EventController.java`
  — version indicators on catalog and booking operations.
- `backend/src/main/java/com/example/ticketing/model/UserProfile.java` —
  align the response/domain profile with persisted display name and provider
  identity where appropriate.
- `backend/src/main/java/com/example/ticketing/model/AuthResponse.java` —
  remove the token field from the JSON response; return only safe profile data.
- `backend/src/main/java/com/example/ticketing/service/UserService.java` —
  replace direct `app_users` JDBC persistence with aggregate/repository use.
- `backend/src/main/java/com/example/ticketing/service/OAuthUserProfileService.java`
  — extract and validate stable provider IDs for both providers.
- Add `backend/src/main/java/com/example/ticketing/model/User.java` (or the
  project's chosen aggregate package) for the Spring Data JDBC user aggregate.
- Add `backend/src/main/java/com/example/ticketing/repository/UserRepository.java`
  for aggregate persistence and provider-identity lookup.
- Add a Flyway migration under
  `backend/src/main/resources/db/migration/` to migrate `app_users` to `users`
  while preserving records.

### Frontend API and auth flow

- `frontend/src/App.tsx` — use versioned API routes in callback, profile, event,
  and booking requests.
- Add `frontend/src/lib/api.ts` (or equivalent) for a shared versioned API base
  and auth-aware request behavior, if this avoids duplicating credential logic.
- Add or update a frontend auth/session module under
  `frontend/src/components/auth/` (or `frontend/src/lib/`) to load the CSRF
  token, make credentialed requests, and call logout. It must never extract or
  persist the JWT.
- `frontend/src/vite-env.d.ts` / frontend environment configuration only if the
  API base needs a new explicit configuration.

### Tests

- Add controller/security tests for native version resolution, supported
  `/api/v1` mappings, auth requirements, cookie extraction and clearing, CSRF
  exchange behavior, and API JSON response types.
- Add service/repository tests for provider ID extraction, persistence,
  existing-user migration compatibility, and provider enum validation.
- Add frontend tests for successful and failed exchange, credentialed requests,
  signed-out behavior, and logout under the cookie policy.
- Update existing
  `backend/src/test/java/com/example/ticketing/service/OAuthUserProfileServiceTests.java`
  for stable provider identity.

## Acceptance criteria

- The backend builds and tests using Java 27, Spring Boot 4, and the selected
  compatible Gradle wrapper.
- Native version configuration is proven active: versioned `/api/v1` handlers
  resolve as `v1`, invalid/unsupported versions fail clearly, and no custom
  regex interceptor is used.
- Auth, events, and booking are reachable at their `/api/v1/...` paths; frontend
  requests use those same paths.
- Google and GitHub successful callbacks resolve a verified email and stable
  provider ID, upsert a `users` aggregate, and complete the cookie credential
  exchange without returning the JWT in JSON.
- The `users` migration preserves existing `app_users` records and maps
  `id`, `email`, `display_name`, `provider`, and `provider_id`.
- Protected requests reject absent, expired, and invalid credentials; public
  event reads remain public.
- Auth endpoints return JSON only. No template rendering is introduced.
- Frontend and backend tests cover both successful and failed authentication
  flows, cookie-only credential handling, CSRF, and logout.

## Verification

- Run the backend Gradle wrapper build and complete test suite using JDK 27.
- Run focused migration tests against a database initialized from the current
  Flyway schema where available.
- Run `npm run build` and the frontend test command once auth-loop tests exist.
- Manually verify provider success/failure, current-user lookup, expired token,
  cookie flags, cookie-only token resolution, logout, CSRF-protected exchange,
  and all versioned URLs.

## Open items

- `[NEEDS CLARIFICATION]` Decide whether logout only expires the browser cookie
  or also revokes the JWT server-side. With stateless JWT validation, a copied
  token remains valid until expiry unless a revocation mechanism is added.
- Confirm whether deployed frontend and API share a site. This determines
  whether `SameSite=Lax` is sufficient or `SameSite=None; Secure` is required.
- The selected Spring Boot 4 release's version-property spelling and path
  segment index must be verified before implementation.
