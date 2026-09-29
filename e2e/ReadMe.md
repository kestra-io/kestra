# End-to-End Testing with Playwright

This package holds the end-to-end (E2E) tests for Kestra, built with [Playwright](https://playwright.dev/).
It is installed on its own, separately from `ui/`: the suite talks to a running Kestra over HTTP and
imports nothing from the frontend source.

## Setup

```bash
npm ci
npx playwright install chromium
```

## Configuration

Copy `.env.example` to `.env` to point the suite somewhere other than the docker backend below.

```env
E2E_BASE_URL=http://localhost:5173
E2E_USERNAME=user@kestra.io
E2E_PASSWORD=DemoDemo1
```

To run against your own instance, launch Kestra in dev mode and use the credentials you set up locally.

## Running Tests

### Against a fresh docker backend

Starts a Kestra image plus Postgres, runs the suite, then tears both down.

```bash
npm run test:e2e
npm run test:e2e -- --kestra-docker-image-to-test kestra/kestra:develop-slim
```

### Against an instance already running

```bash
npm run test:e2e-without-starting-backend
```

### Interactive UI mode, or a single file

```bash
npx playwright test --ui
npx playwright test flow.spec.ts
```

## Checks

```bash
npm run check:types
npm run test:lint
```
