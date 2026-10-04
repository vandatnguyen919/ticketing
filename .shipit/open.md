# Open work

## React screens for email/password auth

The backend in `.shipit/specs/email-password-auth-and-password-reset.md` is
complete (2026-10-04). What is left is the client side.

Next action: add register, forgot password, and reset password screens to the
React app against the finished API: `POST /api/v1/auth/register`,
`POST /api/v1/auth/login`, and `POST /api/v1/auth/password-reset/request`,
`/verify`, `/confirm`. Reuse the existing CSRF bootstrap and cookie handling,
and never read the JWT from JavaScript.

Blocked on nothing.

## Open items carried in the spec

- Decide together whether a JWT should stop working after a password reset,
  for example with a token version claim. Same question as server-side logout
  revocation in the OAuth spec.
- Pick the production SMTP provider, from-address, and reset email template.
  Mailpit is a local and test target only.
- Account linking between an `EMAIL` account and an OAuth provider is out of
  scope for now. Each account has exactly one provider value.
