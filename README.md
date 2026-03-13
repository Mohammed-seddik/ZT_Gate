# Zero Trust Legacy Adapter MVP (Envoy + Keycloak + Client App)

## Folder structure

```text
.
├── certs/
├── client-app/
│   ├── Dockerfile
│   ├── package.json
│   ├── server.js
│   └── views/
│       ├── home.ejs
│       └── public.ejs
├── docker-compose.yml
├── envoy/
│   └── envoy.yaml
├── keycloak/
│   ├── Dockerfile
│   ├── realm/
│   │   └── demo-client-realm.json
│   ├── setup/
│   │   └── setup-realm.sh
│   └── spi/
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/ztam/keycloak/
│           │   ├── RestAuthenticator.java
│           │   ├── RestAuthenticatorFactory.java
│           │   └── VerifyResponse.java
│           └── resources/META-INF/services/
│               └── org.keycloak.authentication.AuthenticatorFactory
├── mysql/
│   └── init.sql
└── scripts/
    └── generate-dev-cert.sh
```

## What this MVP demonstrates

Flow implemented today:

1. User opens app on `https://localhost` (through Envoy).
2. App redirects to Keycloak for login when session is missing.
3. Keycloak login form is rendered.
4. Custom Keycloak Authenticator SPI POSTs credentials to client app `/auth/verify`.
5. Client app validates credentials against its own MySQL users table.
6. Keycloak creates/updates user, sets `db_user_id`, grants roles, finishes login.
7. App receives OIDC callback and shows protected page.

## Build the SPI jar (manual option)

The Docker build already compiles and installs the SPI automatically.

If you want to build the jar manually:

```bash
cd keycloak/spi
mvn -DskipTests package
```

Jar output:

```text
keycloak/spi/target/keycloak-rest-authenticator-1.0.0.jar
```

## Run locally

1. Generate development TLS certificate for Envoy:

```bash
./scripts/generate-dev-cert.sh
```

2. Start stack:

```bash
docker compose up --build
```

3. Open:

- App via Envoy: `https://localhost`
- Keycloak admin (localhost-only bind): `http://localhost:8081`

Admin credentials:

- username: `admin`
- password: `admin`

## Keycloak realm setup

Realm import is automatic from:

- `keycloak/realm/demo-client-realm.json`

It sets:

- realm: `demo-client`
- roles: `admin`, `user`, `viewer`, `editor`
- browser flow: `browser-rest`
- custom authenticator: `rest-authenticator`
- mapper claim: `db_user_id`

Optional helper script exists at:

```bash
./keycloak/setup/setup-realm.sh
```

## Test login

Use demo credentials:

- `alice / password123` (admin)
- `bob / password123` (user)

Expected:

- `alice` login succeeds
- wrong password fails with Keycloak login error
- protected page shows user info, roles, and `db_user_id`

## Verify app traffic only goes through Envoy

- Use `https://localhost` for app access.
- Client app is not published with host ports (only internal Docker network exposure).
- Envoy strips incoming identity headers:
  - `x-user-id`
  - `x-user-roles`
  - `x-user-email`
  - `x-tenant-id`
  - `x-ztam-jwt`

## Verify `/auth/verify` behavior

Inside the Docker network (example from keycloak container):

```bash
docker compose exec keycloak curl -s -X POST http://client-app:3000/auth/verify \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer demo-shared-api-key' \
  -d '{"username":"alice","password":"password123"}'
```

Wrong password should return:

```json
{"valid":false}
```

Wrong API key should return HTTP `401`.

## Security choices for this demo

- Envoy admin bound to `127.0.0.1`.
- Keycloak exposed only on localhost (`127.0.0.1:8081:8080`).
- `/auth/verify` requires shared Bearer API key.
- Passwords are stored hashed (`SHA2-256`) in MySQL.
- Secrets are configurable via environment variables.

## Known limitations (MVP scope)

- Single tenant only.
- Static shared API key.
- No OIDC federation / SAML federation in this demo.
- No policy engine (OPA), fleet orchestration, or Redis session layer.
- Minimal error handling and observability.

## How to evolve this MVP into the better long-term architecture

1. **OIDC/SAML federation first**
   - Integrate customer IdPs via standard federation whenever possible.
   - Prefer IdP-initiated or SP-initiated federation over credential pass-through.

2. **User storage/federation second**
   - Normalize user identity linking, just-in-time provisioning, and role/group mapping.
   - Reduce dependency on per-app credential stores.

3. **Custom REST SPI only as fallback**
   - Keep this legacy REST authenticator for applications that cannot federate yet.
   - Treat it as transitional adapter, not primary architecture.
