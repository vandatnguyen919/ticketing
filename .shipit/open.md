# Open work

## Implement email/password auth and password reset

Spec: `.shipit/specs/email-password-auth-and-password-reset.md` (active, not started).

Next action: add the `com.example.ticketing.auth` packages and the
`V3__add_local_password_credentials.sql` migration, starting with REQ-002 and
REQ-003 (register and login), then REQ-004 to REQ-006 (reset request, verify,
confirm). OTP and ticket state must go through Spring's Cache abstraction
(REQ-007), with the cache provider set to Caffeine for now.

Blocked on nothing. Requires JDK 27 and the Docker Compose Postgres and
Mailpit services for the integration check. Mailpit is already running, with
SMTP on `127.0.0.1:1025` and its UI on `http://127.0.0.1:8025`.

Known follow-ups recorded in the spec open items: JWT revocation after reset,
production SMTP settings, and the React register and reset screens.
