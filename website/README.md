# Ronald Roberts portfolio

React/TypeScript/Vite source for [rnald-roberts-dev-site.vercel.app](https://rnald-roberts-dev-site.vercel.app).

A project-focused portfolio with case studies, source links, and fictional-data demos. It contains no résumé or employment-history section.

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

The existing Vercel project deploys pushes to `main` automatically. Verify GitHub checks on a branch before merging, then confirm the production deployment is ready.

## Visitor flows

- Portfolio → Kalshi demo → fresh/stale/mismatch/closing/slow scenarios.
- Portfolio → reading demo → search “garden” → import → dashboard.
- Expand case studies for implementation details and remaining work.

No live financial data, real login credentials, analytics, or private family records are required by the public demos. Backend setup and limits are in each project's README.

## Editing the portfolio

- `src/content/projects.ts`: project descriptions, case studies, technology lists, and links.
- `src/components/ProjectCard.tsx`: shared project-card presentation.
- `src/App.tsx`: page sections, navigation, and contact links.
- `src/App.css`: layout, colors, responsive rules, and focus styles.

Run the root formatting commands described in [CONTRIBUTING.md](../CONTRIBUTING.md) before committing.
