# @kestra-io/kestra-sdk (OSS)

The JS/TS client for the Kestra **OSS** API. Generated from the OSS OpenAPI spec
(`io.kestra:webserver`), in this repo, on the same commit as the backend it describes.

## Key facts

- **The generated code (`src/openapi/`) is committed to git.** It is *not* regenerated during the
  fast (npm) CI, and building the SDK does *not* require Gradle or the backend. This is what keeps
  CI fast and lets the frontend build without a Java toolchain.
- **Same package name as the public client on purpose.** The app shares this package over module
  federation as `@kestra-io/kestra-sdk`, so plugins loaded into the app resolve to the *same*
  client instance and transparently share auth and tenant routing. The name must not change.
- **One generator plugin for the whole product.** Generation uses
  [`@kestra-io/hey-api-plugin`](../hey-api-plugin), the single shared `@hey-api/openapi-ts` plugin
  (also consumed by the EE SDK and the `client-sdk` repo).
- **Drift is caught by a hash, not by regenerating in CI.** The bottom of `src/openapi/index.ts`
  carries `// openapi-hash: <sha256-16>` — the hash of the `openapi.yml` it was generated from. The
  [`sdk-drift-check`](../../../.github/workflows/sdk-drift-check.yml) workflow regenerates only
  `openapi.yml` (Gradle, with remote cache) and compares hashes.

## Regenerating the SDK

Only needed when the OSS API changes. Never runs in the fast CI.

```bash
# from ui/
npm run generate:sdk    # ./gradlew :webserver:generateOpenapiSpec  +  build the SDK
```

Or step by step from this package:

```bash
npm run generate:openapi   # runs @hey-api/openapi-ts over ../../../openapi.yml,
                           # then stamps the spec hash into src/openapi/index.ts
npm run build              # tsdown-bundles the committed src into dist/
```

## How drift is handled in CI

- **On a PR:** if the committed SDK's hash doesn't match the freshly-generated spec, the
  `sdk-drift-check` workflow comments on the PR asking the author to run `npm run generate:sdk`.
- **On `develop`:** the workflow regenerates, commits the SDK to the `chore/update-sdk` branch, and
  opens (or updates) a PR into `develop`.

## ⚠️ POC branch note

This spike branch had no OSS Gradle toolchain available in the environment it was prepared in, so
the committed `src/openapi/` here was generated from the **combined EE spec as a stand-in** and may
contain operations that only exist in EE. On the first `sdk-drift-check` run on `develop` (which has
Gradle + remote cache) the SDK is regenerated from the true OSS spec and the stand-in is replaced.
