import {client} from "./openapi/client.gen"
import {formDataBodySerializer} from "./openapi/client"
import {OPENAPI_SPEC_HASH} from "./openapi/sdk/shared.gen"
import type {NamespaceLight} from "./openapi/types.gen"
import {createConfigureClient} from "@kestra-io/hey-api-plugin/runtime"
import {createClientFacade} from "./client-facade"

// Types only: the operations live on their per-tag subpaths, or all together on `./all`.
export type * from "./openapi/types.gen"
export type {AxiosLikeConfig, AxiosLikeResponse, AxiosLikeClient, StreamConfig} from "./client-facade"

// The OSS spec models a namespace as `NamespaceLight` ({ id }); the UI consumes a slightly richer
// shape (an optional `description`, populated on EE). Exposed here as the compatibility name the
// shared app imports (`Namespace`) so the app compiles against the OSS SDK unchanged — the EE SDK
// ships its own generated `Namespace`. Scaffolding-only; not a real API surface change.
export type Namespace = NamespaceLight & { description?: string }

declare global {
    interface Window {
        KESTRA_BASE_PATH: string
    }
}

// `configureClient` — the universal fetch setup — lives in the shared runtime package and is bound
// here to this SDK's own client + multipart serializer. It is bundled into this package's dist at
// build time (see tsdown noExternal), so no runtime dependency on @kestra-io/hey-api-plugin is added.
const configure = createConfigureClient(client, formDataBodySerializer)

export const configureClient: typeof configure = (clientConfig) => {
    const result = configure(clientConfig)
    // Dev-only: warn (once) if the committed SDK is stale vs the backend's live OpenAPI spec. The
    // guard + dynamic import make bundlers drop this entirely from production builds (tree-shaken).
    // Also excluded under Vitest (`MODE === "test"`, its documented default): there is no live
    // backend to compare against there — Storybook's `preview.jsx` short-circuits every axios
    // request but calls `configureClient()` on native `fetch` on every single story render, and
    // without this guard each one hits an unmatched dev-server route and logs a bogus mismatch.
    // `import.meta.env` is a Vite construct (not in this package's lib types), hence the cast.
    const env = (import.meta as unknown as {env?: {DEV?: boolean, MODE?: string}}).env
    if (env?.DEV && env.MODE !== "test") {
        void import("./dev-freshness").then((m) => m.warnIfSdkStale(OPENAPI_SPEC_HASH)).catch(() => {})
    }
    return result
}

// useClient()/setMockClient() — the app-only axios-like fetch facade — bound to this SDK's client.
// The EE SDK reuses createClientFacade from this exact module by relative import (DRY).
export const {useClient, setMockClient} = createClientFacade(client, formDataBodySerializer)
