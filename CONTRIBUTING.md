# Developer guide

This repository contains independent Java applications and a static React portfolio. Start with the project README for the part you want to run. No shared backend needs to be started to edit the portfolio or try its fictional-data demos.

## Where to make a change

| Change                                   | Start here                                                                                                                     |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Portfolio text or project links          | `website/src/content/projects.ts`                                                                                              |
| Portfolio cards or page layout           | `website/src/components/ProjectCard.tsx`, `website/src/App.tsx`, `website/src/App.css`                                         |
| Kalshi API parsing or request parameters | `projects/kalshi-brti-monitor/src/main/java/com/ronrob/brti/market/KalshiMarketClient.java`                                    |
| Current-contract discovery or rollover   | `market/MarketDiscovery.java` and `market/MarketContract.java` in the same Java source tree                                    |
| Quote reconciliation or readiness        | `market/MarketSnapshotFactory.java`, `market/MarketSnapshot.java`, `feed/QuoteDecisionMath.java`                               |
| Bitcoin technical indicators             | `feed/BtcTechnicalMath.java`                                                                                                   |
| Reading screens                          | `projects/family-reading-tracker/frontend/src/pages/`                                                                          |
| Reading HTTP transport or demo fixtures  | `projects/family-reading-tracker/frontend/src/services/`                                                                       |
| Reading backend                          | `projects/family-reading-tracker/src/main/java/com/familytracker/`: `controller`, `service`, `repository`, `model`, `security` |

The [Kalshi architecture guide](projects/kalshi-brti-monitor/docs/architecture.md) explains the complete poll and the API compatibility boundary. Reading-tracker authorship and unfinished access controls are documented in its [README](projects/family-reading-tracker/README.md).

## Formatting

Use Node.js 22. From the repository root:

```sh
npm ci
npm run format
npm run format:check
```

Prettier and its Java plugin are pinned in the root lockfile. Java uses four spaces; web source uses two. `.editorconfig` keeps editor whitespace consistent. Generated demo bundles, dependency directories, build output, and lockfiles are excluded from formatting. Do not hand-edit `website/public/demos/`.

Formatting is checked in GitHub Actions alongside the Java tests, frontend lint, and production builds.

## Run the checks

Use Java 21 for both Maven projects. Each wrapper downloads Maven and dependencies on its first run. Commands below start at the repository root; `npm --prefix` selects the relevant frontend without changing directories.

```sh
(cd projects/kalshi-brti-monitor && ./mvnw test)
(cd projects/family-reading-tracker && ./mvnw test)
node --test projects/kalshi-brti-monitor/demo/quality.test.mjs
npm --prefix website ci
npm --prefix website run lint
npm --prefix website run build
npm --prefix projects/family-reading-tracker/frontend ci
npm --prefix projects/family-reading-tracker/frontend run lint
npm --prefix projects/family-reading-tracker/frontend run build
npm --prefix projects/family-reading-tracker/frontend run build:demo
npm run format:check
```

On Windows, use `mvnw.cmd` from each project directory. Java tests use fictional/local fixtures and require no Kalshi credentials. The Kalshi client tests start a loopback HTTP server to exercise real request construction and parsing. A passing test suite does not prove that a live external feed is available.

When a check fails, keep the failure and fix the underlying behavior. Add regression tests for changes to contract selection, timestamps, quote validation, arithmetic, authentication, or response fields. Pure copy or formatting edits generally only need existing checks.

## Data and interface conventions

- Prefer named records at domain boundaries; HTTP parsing belongs in clients, not controllers.
- Keep calculations deterministic. Pass `Clock` or `Instant` where time affects behavior.
- Preserve existing JSON field names when changing Java structure. Kalshi serialization tests pin the public market response; update those fixtures only for an intentional API change and update its consumers together.
- Explain non-obvious rules and assumptions in comments. Names should explain routine code.
- Keep credentials, `.env` files, local research CSVs, and real family records outside commits. `.env.example` and `.env.demo` may contain nonsecret configuration only.

## Publish the portfolio

1. Regenerate demos when their source changes: `node website/scripts/prepare-demos.mjs`.
2. Run formatting, frontend checks, builds, and relevant Java tests. Inspect `git diff --check` and `git diff --stat`.
3. Push a branch and wait for GitHub Actions to pass before merging to `main`.
4. Vercel automatically builds `main` with `website` as its root. Check that the production deployment is ready and references the intended commit.
5. Verify the [live portfolio](https://rnald-roberts-dev-site.vercel.app): expand a case study, change the Kalshi sample scenario, and search/import a fictional book in the reading demo.

The website deploys static frontend assets. It does not deploy either Java backend or expose local research records. Keep the existing Vercel project, domain, and protection settings.
