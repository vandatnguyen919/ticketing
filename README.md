# Event Ticketing Platform MVP

This project is a small, decoupled ticketing platform MVP built around the plan in PRODUCT.md.

## Stack

- Frontend: React + TypeScript + Tailwind
- Backend: Java 27 + Spring Boot 4.1.1
- Security: Spring Security OAuth2 Client + JWT Resource Server
- Persistence: Postgres + Flyway + Spring Data JDBC

## Start the database and Vault

```bash
docker compose up -d
```

This also starts Vault with its UI at http://127.0.0.1:8200/ui. The first time,
initialize and unseal Vault before using it:

```powershell
docker compose exec vault vault operator init
docker compose exec vault vault operator unseal
docker compose exec vault vault operator unseal
docker compose exec vault vault operator unseal
```

Save the initialization output somewhere secure and offline. It contains the
unseal keys and the initial root token. Do not commit or share them. Vault must
be unsealed again after its container restarts; the named Docker volume keeps
its encrypted data across restarts.

Sign in to the UI with the initial root token. In the `kv` KV v2 engine,
create a secret at `ticketing` and add these fields:

- `oauth.github.client-id`
- `oauth.github.client-secret`
- `oauth.google.client-id`
- `oauth.google.client-secret`
- `security.jwt-secret` (at least 32 characters)

The backend is configured to read only `kv/data/ticketing`. To create its
read-only Vault token, run these commands from the project root in PowerShell:

```powershell
docker compose exec vault vault login
docker compose exec vault vault policy write ticketing-backend /vault/policies/ticketing-backend.hcl
docker compose exec vault vault token create -policy=ticketing-backend -field=token
docker compose exec vault rm -f /home/vault/.vault-token
```

The login command prompts for the root token. Keep the token returned by the
token creation command private; it is the backend's `VAULT_TOKEN`. The final
command removes the cached root token from the Vault container's CLI session.
It does not revoke the root token; Vault's CLI does not have a `logout`
command in this image.

Keep Vault's built-in `default` policy on the backend token. It grants no
secret access; it only allows a token to inspect and renew itself, which is
what the verification commands below use. Creating the token with
`-no-default-policy` makes those checks fail with 403 even when the read path
is correct.

To confirm the token can read the secret path, set it in PowerShell and run:

```powershell
$secureToken = Read-Host "Paste the read-only backend token" -AsSecureString
$env:VAULT_TOKEN = (New-Object System.Net.NetworkCredential("", $secureToken)).Password

docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN ticketing-vault vault token lookup
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN ticketing-vault vault token capabilities kv/data/ticketing
```

`vault token lookup` should list the `ticketing-backend` policy, and
`vault token capabilities kv/data/ticketing` should print `read`.

## Start the backend

```bash
cd backend
./gradlew bootRun
```

On Windows PowerShell, use `.\gradlew.bat bootRun`.
Before starting, set the read-only Vault token in the same shell. For local
HTTP development, also disable the secure-cookie flag:

```powershell
$env:VAULT_TOKEN = '<read-only backend token>'
$env:AUTH_COOKIE_SECURE = 'false'
.\gradlew.bat bootRun
```

`VAULT_ADDR` defaults to `http://127.0.0.1:8200`; override it if Vault is hosted
elsewhere. OAuth client IDs, OAuth client secrets, and the JWT signing key are
loaded from Vault at startup. The backend does not use the initial root token.
Flyway applies pending database migrations automatically when the backend starts.

## Start the frontend

```bash
cd frontend
npm install
npm run dev
```

## Verify

From the project root, run all configured checks:

```powershell
.\verify.ps1
```

Run one check by name when iterating:

```powershell
.\verify.ps1 -Check frontend
.\verify.ps1 -Check backend
```

The frontend check runs auth/API tests and the TypeScript/Vite production build.
The backend check runs the Gradle test lifecycle using the Java 27 toolchain.

## Demo flow

1. Open the frontend at http://localhost:5173.
2. Choose GitHub or Google. The backend starts the OAuth 2.0 Authorization Code flow and redirects to the frontend callback after provider authentication.
3. Choose an available event and review its ticket details.
4. Reserve the ticket. The backend decrements inventory atomically.

## OAuth provider setup

Register these callback URLs in the provider applications:

- GitHub: `http://localhost:8080/login/oauth2/code/github`
- Google: `http://localhost:8080/login/oauth2/code/google`

Store the corresponding OAuth client IDs and secrets in Vault as described
above. Set `APP_FRONTEND_URL` if the frontend is not at
`http://localhost:5173`. `AUTH_COOKIE_SECURE` defaults to `true`; only set it
to `false` for local HTTP development.

The Compose Vault listener uses HTTP and is bound to loopback for this local
development setup. It is not a production deployment configuration. Use TLS,
secure unseal/auto-unseal, and an appropriate deployment architecture before
exposing Vault to other machines.

## API contract

- `GET /api/v1/events`
- `POST /api/v1/events/{eventId}/book` (credential cookie + CSRF header)
- `GET /api/v1/auth/csrf` and `GET /api/v1/auth/providers`
- `POST /api/v1/auth/exchange-session` (CSRF-protected; sets the JWT cookie and returns a safe profile)
- `GET /api/v1/auth/me` (credential cookie)
- `POST /api/v1/auth/logout` (CSRF-protected; expires the JWT cookie)

The frontend includes credentials on API calls; it does not read or persist the
JWT or send an Authorization bearer header.

This project keeps the MVP narrow and intentionally excludes payment, seat maps, and admin tooling.
