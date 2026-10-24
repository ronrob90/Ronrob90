# Family Reading Tracker — React rebuild

An ongoing rebuild of **Book Worm Buddies**, a collaborative Tech Elevator capstone. The original used Vue, Spring Boot, JDBC, and PostgreSQL. This folder contains the newer React, Spring Data JPA, and H2 version.

## Credits and scope

The original capstone team was **Ronald Roberts, Dominique “Dom” Johnson, Sonal Purwar, and Jamesson Kolleno**. The capstone is team work; this later rebuild does not imply sole authorship of the original application.

This version is part of Ronald Roberts's continued project work. It preserves the available rebuild source and includes publication cleanup: restored local configuration and JWT support, authentication regression tests, frontend fixes, and a fictional-data demo. The original Vue capstone is not included here.

| Area | Current state |
| --- | --- |
| Accounts | Registration, BCrypt password storage, login, one-hour JWTs, and bearer-token validation |
| Frontend | React login/registration, family dashboard, book search, and book import |
| Persistence | JPA entities/repositories and an in-memory H2 database for local development |
| External books | Open Library search/import through the backend |
| Other workflows | Reading-session, milestone, and prize backend endpoints; complete UI workflows remain unfinished |
| Public demo | Fictional family/catalog, dashboard, search, and import; no backend or account required |

## Run locally

Requires Java 21 and Node.js 22. Dependencies need internet access on first installation.

From this folder, start the backend:

```sh
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`. In a second terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open the URL printed by Vite. Its development proxy forwards `/auth` and `/api` to `http://127.0.0.1:8080`. Register a local test account, then log in. Searches require Open Library to be reachable from the backend.

The in-memory database resets on restart. By default, the JWT signing key is generated at startup, so restart invalidates sessions. Optional `READING_TRACKER_JWT_SECRET` supplies a base64-encoded random key of at least 32 bytes. Do not commit a signing secret. A separately hosted frontend can set `VITE_API_BASE_URL`, but public hosting requires completing the access controls below.

## Sample-data demo

```sh
cd frontend
npm ci
npm run build:demo
```

Search “library,” “garden,” or “explorers,” import a result, and return to the dashboard. The family and books are fictional. Changes exist only in memory and reset on reload. This build makes no external API calls and uses no real login credentials.

The demo is packaged at `/demos/reading-tracker/` in the portfolio. See [website instructions](../../website/README.md) to regenerate it.

## Verification

```sh
./mvnw test
cd frontend
npm ci
npm run lint
npm run build
npm run build:demo
```

Backend tests cover startup, rejected anonymous/invalid-token requests, registration/login, authenticated book import, password-field serialization, and rejection of administrative role registration. The test-only Mockito subclass configuration avoids JVM agent attachment and does not change application authentication.

## Remaining work

This is a **local prototype**. API access requires authentication, but family ownership and per-record permissions are not enforced throughout the controllers. An authenticated account can access shared records. Do not deploy this backend publicly or enter private family data.

Next steps: family-scoped authorization and negative-access tests, complete reading-progress/milestone screens, durable persistence and migrations, broader validation/error handling, and dependency review before any production deployment.
