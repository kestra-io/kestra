# @kestra-io/hey-api-plugin

The **single, shared** [`@hey-api/openapi-ts`](https://heyapi.dev/) plugin used to generate every
Kestra JS/TS SDK. It turns a Kestra OpenAPI spec into tenant-aware, human-friendly SDK wrapper
functions (stable, readable method names — grouped per tag — that transparently inject the current
tenant into the request path).

## Why this package exists

Historically this generator plugin was copy-pasted into every place that generated a Kestra SDK
(the OSS UI, the EE UI, and the `client-sdk` repo). Three copies meant three things to keep in sync.

This package is the one source of truth. It is maintained here, in the OSS monorepo, and consumed
everywhere:

| Consumer | How it depends on this package |
|----------|-------------------------------|
| `@kestra-io/kestra-sdk` (OSS UI, this monorepo) | workspace dependency |
| `@kestra-io/kestra-sdk` (EE UI, `kestra-ee`) | workspace dependency (points at this OSS package) |
| `client-sdk` (JS SDK) | published npm dependency |

## Publishing

This package is **published to npm manually**, on demand, via the
[`publish-hey-api-plugin.yml`](../../../.github/workflows/publish-hey-api-plugin.yml) GitHub
workflow (`workflow_dispatch`). Bump `version` in `package.json`, then trigger the workflow — it
builds `dist/` and runs `npm publish`. Publishing is intentionally **not** tied to any spec change
or SDK regeneration: the plugin's release cadence is independent from the SDKs it generates.

## Usage

```ts
// openapi-ts.config.ts
import { defineConfigKestraHeyOptionalTenant } from "@kestra-io/hey-api-plugin"

export default {
    input: "openapi.yml",
    output: { path: "./src/openapi" },
    plugins: [
        { name: "@hey-api/client-axios", throwOnError: true },
        { name: "@hey-api/sdk", paramsStructure: "flat" },
        defineConfigKestraHeyOptionalTenant(),
    ],
}
```

## Development

```bash
npm run build      # bundle to dist/ via tsdown
npm run typecheck  # tsc --noEmit
```

`dist/` is produced by tsdown and is what gets published; it is git-ignored. `prepare` rebuilds it
on `npm install`, so workspace consumers always resolve a fresh build.
