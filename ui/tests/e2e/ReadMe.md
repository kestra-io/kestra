# End-to-End Testing with Playwright

This directory contains end-to-end (E2E) tests for the Kestra UI, built using [Playwright](https://playwright.dev/).

## Setup

1.  Install dependencies:
```bash
npm install
```

2.  Install Playwright browsers:

```bash
npx playwright install
```

## Configuration

Create a `.env` file in the `kestra/ui/tests/e2e` directory to configure the test target and credentials.

Example `.env` content:

```env
KESTRA_UI_E2E_BASE_URL=http://localhost:5731
KESTRA_UI_E2E_USERNAME=admin
KESTRA_UI_E2E_PASSWORD=password
```

To run tests against your local Kestra instance, launch Kestra in dev mode and use the credentials you have set up locally.

## Running Tests

### Run all tests

```bash
npm run test:e2e-without-starting-backend
```

### Run tests in UI mode

This opens an interactive UI to explore and run tests.

```bash
npm run test:e2e-without-starting-backend --ui
```

### Run a specific test file

```bash
npm run test:e2e-without-starting-backend tests/e2e/your-test-file.spec.ts
```
