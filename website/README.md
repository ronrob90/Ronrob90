# Ronald Roberts portfolio

React/TypeScript/Vite source for [rnald-roberts-dev-site.vercel.app](https://rnald-roberts-dev-site.vercel.app).

This update retains the existing dark style, adds Kalshi and reading-tracker case studies, links projects directly to their source or overview, and includes Nemours experience for April–October 2026.

## Development

Requires Node.js 22.

```sh
npm ci
npm run dev
```

## Demos and build

Two self-contained fictional-data demos are committed in `public/demos/`, so the site can build without Java servers or API keys.

After editing demo source, run from this directory:

```sh
node scripts/prepare-demos.mjs
npm run lint
npm run build
npm run preview
```

The script installs the reading frontend from its lockfile, builds demo mode, and copies the standalone Kalshi example. `public/demos/` is generated content: edit the projects' source and regenerate.

A normal `npm run build` uses committed demo assets. For deployment from this monorepo, use `website` as project root, the Vite preset, `npm run build`, and `dist` output. Preserve the existing Vercel project and domain.

Publishing source does not itself update the existing Vercel domain. Deployment must complete separately in the site's Vercel workspace.

## Visitor flows

- Portfolio → Kalshi demo → fresh/stale/mismatch/closing/slow scenarios.
- Portfolio → reading demo → search “garden” → import → dashboard.
- Expand case studies for implementation details and remaining work.

No live financial data, real login credentials, analytics, or private family records are required by the public demos. Backend setup and limits are in each project's README.
