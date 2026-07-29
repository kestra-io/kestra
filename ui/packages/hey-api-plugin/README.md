# @kestra-io/hey-api-plugin

The **single, shared** package behind every Kestra JS/TS SDK. It ships **two entry points**, one per
lifecycle:

| Import | Entry | Lifecycle | For |
|--------|-------|-----------|-----|
| `@kestra-io/hey-api-plugin` | codegen | generation-time (`devDependency`) | the [`@hey-api/openapi-ts`](https://heyapi.dev/) plugin — turns a Kestra OpenAPI spec into tenant-aware, human-friendly SDK wrappers (stable per-tag method names that inject the current tenant into the path) |
| `@kestra-io/hey-api-plugin/runtime` | runtime | shipped in the SDK bundle | `createConfigureClient(client, formDataBodySerializer)` — the universal `@hey-api/client-fetch` setup every Kestra SDK needs (string/empty-body serializer, the QueryFilter query serializer, Content-Type/Accept fixes, Error normalization) |

The two are deliberately separate: the codegen entry is used only while generating, the runtime entry
is what runs in the browser. `createConfigureClient` is the "useful for everyone" half of the old
hand-written `src/index.ts`; the app-only half (the `useClient`/`setMockClient` singleton and the
axios-like fetch facade) stays in the apps, not here.

Both halves are **fetch-based** (`@hey-api/client-fetch`). There is no axios anywhere in a Kestra
SDK anymore.

## Why this package exists

Historically this generator plugin was copy-pasted into every place that generated a Kestra SDK
(the OSS UI, the EE UI, and the `client-sdk` repo). Three copies meant three things to keep in sync.

This package is the one source of truth. It is maintained here, in the OSS monorepo, and consumed
everywhere:

| Consumer | How it depends on this package |
|----------|-------------------------------|
| `@kestra-io/kestra-sdk` (OSS UI, this monorepo) | npm workspace dependency |
| `@kestra-io/kestra-sdk` (EE UI, `kestra-ee`) | npm workspace dependency (points at this OSS package) |
| `client-sdk` (JS SDK) | GitHub Release **tarball URL** (not npm — see Releasing) |

## Runtime: `createConfigureClient`

```ts
import { createConfigureClient } from "@kestra-io/hey-api-plugin/runtime"
import { client } from "./openapi/client.gen"
import { formDataBodySerializer } from "./openapi/client"

// The multipart serializer is passed in, not imported here: client-fetch vendors a self-contained
// core into each SDK, so it is a per-SDK module instance. The request interceptor detects multipart
// endpoints by identity against it. This keeps the runtime dependency-free.
export const configureClient = createConfigureClient(client, formDataBodySerializer)
```

### Signaling Enterprise-only routes (`EnterpriseFeatureError` / `SdkVersionMismatchError`)

`client-sdk` is the **one published SDK** and it ships every method — OSS and Enterprise Edition
alike — because it can't know at publish time which kind of server a consumer will point it at. A
call to an EE-only method (e.g. `listAuditLogs`) against an OSS server 404s, indistinguishable at
first glance from an ordinary "resource not found" 404.

`createConfigureClient` takes an optional third argument, `enterpriseFeature`, so that consumer can
turn that 404 into a typed, actionable error instead of the generic normalized `Error` —
`EnterpriseFeatureError` when the server is OSS, `SdkVersionMismatchError` when it reports itself as EE:

```ts
import {
    createConfigureClient,
    EnterpriseFeatureError,
    SdkVersionMismatchError,
} from "@kestra-io/hey-api-plugin/runtime"
import { client } from "./openapi/client.gen"
import { formDataBodySerializer } from "./openapi/client"

export const configureClient = createConfigureClient(client, formDataBodySerializer, {
    // method + the *templated* OpenAPI path (e.g. "/api/v1/{tenant}/audit-logs/search"),
    // never the resolved URL — path params aren't substituted at this point.
    matchRoute: (method, path) => ENTERPRISE_ONLY_ROUTES[`${method} ${path}`],
    docsUrl: (feature) => `https://kestra.io/docs/enterprise-edition/${feature}`,
    contactSalesUrl: (feature) =>
        `https://kestra.io/contact-sales?utm_source=sdk&utm_medium=error&feature=${feature}`,
})
```

```ts
try {
    await listAuditLogs()
} catch (e) {
    if (e instanceof EnterpriseFeatureError) {
        // e.feature, e.docsUrl, e.contactSalesUrl are structured data — build your own
        // "Upgrade to unlock X" UI instead of parsing e.message.
    }
    if (e instanceof SdkVersionMismatchError) {
        // The server confirmed it is EE (X-Kestra-Edition: EE) yet the route is missing:
        // an SDK/server version skew, not a licensing problem. e.feature is the route's
        // feature id. Prompt to align versions — never "upgrade to Enterprise".
    }
}
```

Which of the two you get is decided by the `X-Kestra-Edition` and `X-Kestra-Route-Matched` headers Kestra
sets on every 404 (server side: `NotFoundHeadersFilter`). A 404 carrying `X-Kestra-Route-Matched: true` came
from application code running a real lookup, so it is always a genuine not-found and is never turned into
either error, regardless of what `matchRoute` says. Servers predating these headers fall back to the
`matchRoute`-plus-edition heuristic, so behavior against them is unchanged.

`SdkVersionMismatchError` deliberately does **not** extend `EnterpriseFeatureError`: the two call for opposite
advice ("align your versions" vs "upgrade to Enterprise"), so conflating them would send an EE customer to
sales over a version skew. A consumer matching only `EnterpriseFeatureError` therefore stops catching the
EE-server case and needs the second branch above.

`ENTERPRISE_ONLY_ROUTES` is deliberately **not** built by this package: computing it (e.g. diffing
the EE spec's operations against the OSS spec at generation time) is a `client-sdk`-only concern —
the OSS and EE UI SDKs never need it and shouldn't pay for it. Omit `enterpriseFeature` entirely and
behavior is unchanged from before this option existed.

## Codegen: the plugin

```ts
// openapi-ts.config.ts
import { defineConfigKestraHeyOptionalTenant, fixYamlSourceRequestBodyContentType } from "@kestra-io/hey-api-plugin"

export default {
    input: "openapi.yml",
    parser: { patch: { operations: fixYamlSourceRequestBodyContentType } },
    output: { path: "./src/openapi" },
    plugins: [
        { name: "@hey-api/client-fetch", throwOnError: true },
        { name: "@hey-api/sdk", paramsStructure: "flat" },
        defineConfigKestraHeyOptionalTenant(),
    ],
}
```

## Spec-hash stamping (`specPath`)

Pass `specPath` (the raw OpenAPI spec file) to the codegen plugin and it stamps
`export const OPENAPI_SPEC_HASH = sha256(specFile)[:16]` into the generated SDK — no external bin or
post-process step, the plugin does it from within its handler. The committed OSS/EE SDKs carry this
hash so a dev-time staleness check can fetch the backend's live spec, hash it the same way, and warn
if the checked-in SDK has drifted. Consumers that don't need it (e.g. client-sdk, which regenerates
on publish) simply omit `specPath`.

## Releasing (no npm)

This package is **not published to npm**. The OSS and EE UIs consume it as an npm workspace, so they
never need a release. Only the external `client-sdk` repo consumes it, and it does so via a **GitHub
Release tarball URL** pinned in its `package.json`.

To cut a release: bump `version` here in a normal commit, then run the
[`publish-hey-api-plugin.yml`](../../../.github/workflows/publish-hey-api-plugin.yml) workflow
(`workflow_dispatch`). It typechecks + builds, `npm pack`s the package, and creates a GitHub Release
tagged `hey-api-plugin-v<version>` with the `.tgz` attached. Then bump the URL in client-sdk's
`package.json` to the new version and run `npm install` there to refresh its lockfile. Releasing is
intentionally **not** tied to any spec change or SDK regeneration.

`dist/` is produced by `tsdown` and is what the tarball ships (via the `files` allowlist); it is
git-ignored. There is **no** `prepare` script (it would run on every `npm ci` and break
lockfile-only installs); `dist/` is rebuilt by `prepublishOnly` when packing and, for local
workspace consumers, by the OSS `ui/scripts/ensure-sdk.mjs` bootstrap.

## Development

```bash
npm run build      # bundle both entries to dist/ via tsdown
npm run typecheck  # tsc --noEmit
```
