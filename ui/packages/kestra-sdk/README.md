# @kestra-io/kestra-sdk (OSS)

The JS/TS client for the Kestra **OSS** API, generated from the backend's own OpenAPI spec and
living **in the same repo, on the same commit** as the backend it describes.

- **Generated from:** `io.kestra:webserver` → `./gradlew :webserver:generateOpenapiSpec` → `openapi.yml`
- **Generator:** [`@hey-api/openapi-ts`](https://heyapi.dev/) with `@hey-api/client-fetch` + the shared
  [`@kestra-io/hey-api-plugin`](../hey-api-plugin) (tenant-aware, human-friendly wrappers).
- **Committed:** the generated code under [`src/openapi`](src/openapi) **is checked into git**. This
  decouples the fast (npm) build from the Gradle/backend build — `npm run dev`, `build`, and
  `check:types` never need a Java toolchain.

The package keeps the name `@kestra-io/kestra-sdk` because it is the module-federation-shared client:
the app and plugins must resolve one client instance (shared auth + routing).

## Regenerating the SDK

Only needed when the OSS API changes. From `ui/`:

```bash
npm run generate:sdk
```

That is the **only** path that invokes Gradle. It: generates `openapi.yml` (Gradle) → hashes it and
compares against the `OPENAPI_SPEC_HASH` already committed in `src/openapi/sdk/shared.gen.ts` → if
they match, the committed SDK is already correct for the current spec and the rest is skipped; if
they differ, builds the shared plugin → runs `openapi-ts` (generate + convert + hash-stamp) →
bundles `dist/`. This makes `generate:sdk` cheap to run speculatively (e.g. after any backend change)
since a no-op spec diff short-circuits before the expensive steps. Commit the resulting
`src/openapi/` changes (and the regenerated `package.json` `exports` map) when it does regenerate.

Everyday commands never regenerate: `ui/scripts/ensure-sdk.mjs` (the `predev` / `prebuild` /
`precheck:types` hook) only bundles the already-committed `src/openapi` into `dist/` when `dist/` is
missing.

## Drift detection

The generated SDK exports `OPENAPI_SPEC_HASH` (`sha256(openapi.yml)[:16]`, stamped by the shared
plugin at generation time — no external bin). Drift is caught **at dev time, not in CI**: on the
first `configureClient` call in a dev build, `dev-freshness.ts` fetches the backend's live spec,
hashes it the same way, and warns if the committed SDK is behind. The check is guarded by
`import.meta.env.DEV` + a dynamic import, so it is tree-shaken out of production builds entirely.

## Runtime

`src/index.ts` binds the shared `createConfigureClient(client, formDataBodySerializer)` from
`@kestra-io/hey-api-plugin/runtime` (bundled into `dist/`, not a runtime dependency) and keeps the
app-only `useClient()` / `setMockClient()` — an axios-like facade over fetch that shares the same
interceptors, so existing `useClient().get/post(...)` call sites are unchanged. The EE SDK reuses
these by relative import.
