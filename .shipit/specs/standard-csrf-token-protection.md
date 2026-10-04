# Standard SPA CSRF token protection

## Goal

Standardize CSRF protection for the React SPA and Spring Boot 4 / Spring Security 7 API on Spring Security's SPA configuration and the cookie-to-header pattern. Because authentication uses an automatically attached HttpOnly JWT cookie, CSRF protection remains required for every unsafe request.

The target flow is: Spring issues a raw CSRF token in a JavaScript-readable `XSRF-TOKEN` cookie; the React API client copies it to `X-XSRF-TOKEN` on unsafe requests; Spring validates the header against the expected cookie token. The authentication cookie stays HttpOnly and is never read by React.

## Current behavior

- The backend uses `CookieCsrfTokenRepository.withHttpOnlyFalse()` in `SecurityConfig` and scopes the CSRF cookie to `/api`, with `Secure` controlled by `AUTH_COOKIE_SECURE` and `SameSite=Lax`.
- `GET /api/v1/auth/csrf` returns the token and header name as JSON. The React `apiFetch` helper fetches that endpoint, caches the token in memory, adds the header to unsafe requests, and retries once after a 403 with a fresh token.
- React currently uses `fetch`, not Axios. All API requests include credentials. The backend allows explicit frontend origins and the `X-XSRF-TOKEN` request header. Axios is not currently a frontend dependency.
- Existing backend and frontend tests cover basic CSRF enforcement and header construction, but backend integration tests use Spring's synthetic `.csrf()` post-processor rather than proving the cookie/header exchange.

## Requirements

### REQ-001: Keep CSRF on cookie-authenticated unsafe requests

- Keep Spring Security CSRF enabled for unsafe methods, including `POST`, `PUT`, `PATCH`, and `DELETE`.
- Do not add a bearer-header bypass or disable CSRF for `/api/v1/**`. The API uses an ambient HttpOnly JWT cookie, so browser requests can carry authentication automatically.
- Keep public unsafe authentication operations, including OAuth session exchange, logout, and any future registration or password-reset endpoints, CSRF-protected.

### REQ-002: Use Spring Security's SPA support

- Configure the Spring Security 7 SPA CSRF request handling (`csrf.spa()` or the equivalent supported by the Boot-managed Spring Security version).
- Explicitly protect requests authenticated by the resource server. Spring Security's resource-server configurer automatically exempts bearer-token requests from CSRF; because this application resolves bearer tokens from an ambient cookie, override the final `CsrfFilter` matcher so cookie-authenticated unsafe requests still require a CSRF token.
- Do not rotate the CSRF cookie on every JWT-authenticated request. Skip the CSRF authentication-session strategy for `JwtAuthenticationToken`; retain rotation for interactive authentication. If login clears the cookie, the SPA bootstraps a fresh one before its next unsafe request.
- Retain `CookieCsrfTokenRepository` with the cookie name `XSRF-TOKEN`, header name `X-XSRF-TOKEN`, `HttpOnly=false`, `SameSite=Lax`, and the existing environment-controlled `Secure` behavior.
- Ensure the SPA handler resolves the raw cookie token from the request header while preserving Spring's BREACH-aware handling for request attributes. Do not replace it with an unconditional raw-token handler.
- Ensure a safe initialization request materializes the deferred token and sets the cookie before the first unsafe request. The existing CSRF endpoint may remain as a public initialization endpoint, but it must not return a token value in its body, masked or raw (prefer `204 No Content`). The frontend must not depend on a token in JSON.
- Scope the readable CSRF cookie so JavaScript running at the SPA route `/` can read it. With the current React page at `/`, use a cookie path visible at `/` (normally `/`) rather than `/api`. Keep the JWT cookie scoped and HttpOnly as it is today.

### REQ-003: Mirror the cookie in the React API client

- Add Axios and use a shared Axios client for frontend API requests. Keep API origin/version and unauthorized-response handling centralized rather than calling Axios directly from components.
- Initialize the CSRF cookie through a credentialed safe request before the first unsafe request when the cookie is absent.
- Configure `withCredentials: true`, `xsrfCookieName: 'XSRF-TOKEN'`, and `xsrfHeaderName: 'X-XSRF-TOKEN'`. Set `withXSRFToken` explicitly for cross-origin requests, using a callback that enables it only for unsafe methods. This is required because the local frontend and API use different ports, and avoids sending the CSRF header on safe reads.
- Let Axios read the raw `XSRF-TOKEN` cookie and attach its value as `X-XSRF-TOKEN` on unsafe requests only. Do not parse or cache the CSRF token in application code.
- Read the cookie at request time or otherwise handle token rotation correctly; do not rely on a long-lived cached value after authentication, logout, or a CSRF rejection.
- Continue sending credentials on API requests. Retain the bounded one-time recovery behavior for a stale token only if tests prove it cannot duplicate non-idempotent business effects; otherwise return the 403 and require explicit token refresh/retry by the caller.
- Do not expose or store the JWT in JavaScript, local storage, or the CSRF cookie.

### REQ-004: Preserve browser boundary protections

- Keep credentialed CORS restricted to configured frontend origins, and allow the `X-XSRF-TOKEN` header for those origins only.
- Keep `Secure` enabled in production and `SameSite=Lax` for the current same-site deployment. Document any deployment that requires cross-site cookies as needing an explicit `SameSite=None; Secure` decision and an exact CORS origin.
- Treat the readable CSRF cookie as a request-integrity token, not as an authentication credential. Same-Origin Policy alone is not the security guarantee: CSRF relies on the attacker being unable to read the token and unable to submit the required custom header under the configured CORS policy. Same-origin XSS can still read it.
- The cookie-to-header approach assumes the SPA can read a cookie for the API host. The current local URLs use `localhost` on different ports, which share a cookie host; the cookie path must also match the SPA page. If production uses different hostnames, do not broaden the cookie `Domain` without a security review. Use a same-origin API proxy or retain a CSRF bootstrap response readable only by the configured SPA origin.

## Implementation plan

1. Update `backend/src/main/java/com/example/ticketing/config/SecurityConfig.java` to use Spring Security 7 SPA CSRF handling while preserving the configured cookie name, header, path visibility, `Secure`, and `SameSite` attributes. Override the resource-server bearer-token CSRF exemption because the bearer token is carried in a cookie, and skip per-request CSRF rotation for JWT authentication while preserving interactive-login rotation.
2. Update `backend/src/main/java/com/example/ticketing/controller/AuthController.java` so `GET /api/v1/auth/csrf` initializes the deferred cookie and returns no token value (prefer `204 No Content`). Keep the route public and safe.
3. Add Axios to `frontend/package.json` and the lockfile. Replace the `fetch` transport in `frontend/src/lib/api.ts` with a shared Axios instance configured with the versioned base URL, credentials, cookie/header names, and unsafe-method-only `withXSRFToken` behavior. Preserve centralized unauthorized handling and expose a stable API helper to callers.
4. Update API consumers in `frontend/src/App.tsx` and `frontend/src/lib/auth.ts` for the shared Axios helper and its response/error shape. Ensure the CSRF bootstrap call is credentialed and its response body is ignored; the next unsafe request uses the cookie issued by that call. Remove obsolete JSON-token caching and refresh paths only after equivalent rotation behavior is covered.
5. Update backend tests in `backend/src/test/java/com/example/ticketing/ApiVersionAndCookieAuthTests.java` to verify the readable CSRF cookie name/path/flags, rejection when the header is missing or mismatched on authenticated booking requests, acceptance when the header mirrors the cookie, no CSRF cookie rotation on a successful JWT booking, and issuance/refresh around the supported auth lifecycle.
6. Update frontend tests in `frontend/src/lib/api.test.ts` and `frontend/src/lib/auth.test.ts` to verify Axios configuration, credential inclusion, omission of the CSRF header on safe methods, cookie-to-header mirroring on unsafe methods, token rotation, unauthorized handling, and failure behavior. Use Axios's test adapter or an existing Axios mocking pattern rather than mocking global `fetch`. Keep CORS preflight coverage for the custom header.
7. Run the focused frontend test/build checks and backend tests, then manually verify the browser flow using the local frontend (`localhost:5173`) and backend (`localhost:8080`).

## Acceptance criteria

- Unsafe requests without a CSRF header or with a value different from the `XSRF-TOKEN` cookie receive 403; unsafe requests with the matching token proceed to normal authorization and controller handling.
- A public safe initialization request issues the CSRF cookie before the first unsafe request.
- The CSRF cookie is readable by the React page at `/`, has the required name and header pairing, uses `SameSite=Lax`, and is `Secure` in production. The JWT cookie remains `HttpOnly` and is not exposed to JavaScript.
- Axios sends `X-XSRF-TOKEN` on unsafe methods only, copies the current cookie value exactly, and includes credentials.
- An authenticated booking without a CSRF header is rejected; a valid cookie/header pair succeeds without rotating the CSRF cookie on that JWT-authenticated request.
- OAuth session exchange, event booking, logout, and future public unsafe auth routes remain CSRF-protected.
- Credentialed CORS remains limited to explicit frontend origins and permits the configured custom header.
- There is no bearer-header CSRF bypass, no reliance on Axios's same-origin-only default for cross-origin requests, and no masked or raw CSRF token in the bootstrap endpoint's response body.

## Verification

- Backend: run `backend/gradlew.bat test` using the configured JDK 27 toolchain. Include focused MockMvc tests with an actual cookie/header pair rather than relying only on `.with(csrf())`.
- Frontend: run `npm test` and `npm run build` from `frontend`; verify Axios sends the header on unsafe cross-origin requests only when the cookie is readable.
- Browser check: from the SPA at `/`, confirm `document.cookie` can read the CSRF cookie, confirm the JWT cookie is not readable, then verify booking and logout send the matching header and succeed. Verify missing/mismatched headers fail with 403.
- Configuration check: verify local HTTP permits the configured non-Secure cookie only when `AUTH_COOKIE_SECURE=false`; verify production configuration emits `Secure` and `SameSite=Lax`.

## Open items

- Confirm the production frontend/API hostnames and whether they share a cookie-visible host. If they do not, the direct `document.cookie` pattern cannot read a host-only API cookie; choose a same-origin proxy or an origin-restricted bootstrap endpoint rather than widening cookie scope by default.
- Automatic retries after a 403 are not used. The client returns the error and does not replay a modifying request.

## References

- Spring Security 7 CSRF reference: [CSRF protection](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)
- Axios request configuration: [Request config](https://axios-http.com/docs/req_config)
- Current backend security: `backend/src/main/java/com/example/ticketing/config/SecurityConfig.java`
- Current frontend API client: `frontend/src/lib/api.ts`
