import type {UserConfig} from "@hey-api/openapi-ts"
import * as path from "path"
import {fileURLToPath} from "url"
import {defineConfigKestraHeyOptionalTenant, fixYamlSourceRequestBodyContentType, normalizeQueryFilterParams, widenQueryFilterValue, replaceFlowLabels} from "@kestra-io/hey-api-plugin"

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const generateHash = (str: string) => {
    let hash = 0
    for (const char of str) {
        hash = (hash << 5) - hash + char.charCodeAt(0)
        hash |= 0 // Constrain to 32bit integer
    }
    return hash.toString(16).replace("-", "0")
}

// Input is the OSS-only spec, produced by `./gradlew :webserver:generateOpenapiSpec` (written to the
// repo root as openapi.yml, which is git-ignored). Unlike client-sdk, there is no
// kestra-openapi-sdk-customizer sanitizer pass — that is a client-sdk-only concern.
//
// The generated output under ./src/openapi is COMMITTED to git — it is not regenerated in the
// fast (npm) CI. Regeneration is an explicit, Gradle-bound step (`npm run generate:sdk` from ui/).
// The plugin stamps `OPENAPI_SPEC_HASH = sha256(openapi.yml)[:16]` into the generated SDK (see its
// `specPath` option below) so the dev-time staleness check can detect drift cheaply — no external
// stamp bin or postProcess step (see src/dev-freshness.ts and README.md).
//
// Note: unlike client-sdk, this pipeline does NOT run convert-openapi-sdk-functions.mjs (the
// arrow-fn → function-declaration rewrite). That transform exists only to make client-sdk's test
// coverage readable, and all @kestra-io/kestra-sdk API testing lives in the client-sdk repo — the
// app consumes the SDK, it does not test it — so the transform is not needed here.
const specPath = path.resolve(__dirname, "../../../openapi.yml")

export default {
    input: specPath,
    parser: {
        patch: {
            operations: (method: string, path: string, operation: any) => {
                // hey-api prefers the application/json variant when resolving a request body; force
                // application/x-yaml for YAML-source bodies (client-sdk issue #340).
                fixYamlSourceRequestBodyContentType(method, path, operation)
                // Make required QueryFilter[] `filters` params optional (fixes hey-api's broken
                // array serializer + lets callers omit an empty filters array).
                normalizeQueryFilterParams(method, path, operation)
            },
            schemas: {
                // Widen QueryFilter.value to `unknown` so callers can assign scalars/arrays directly.
                QueryFilter: widenQueryFilterValue,
                // Flow labels are a Label[] to the UI, not a raw string map.
                Flow: replaceFlowLabels,
                AbstractFlow: replaceFlowLabels,
                FlowWithSource: replaceFlowLabels,
            },
        },
    },
    output: {
        path: path.resolve(__dirname, "./src/openapi"),
    },

    plugins: [
        {
            name: "@hey-api/client-fetch",
            throwOnError: true,
        },
        {
            name: "@hey-api/sdk",
            paramsStructure: "flat",
            operations: {
                methodName(operation) {
                    return `__${generateHash(operation)}__`
                },
            },
        },
        // The one openapi-ts plugin for the whole product: EE's kestra-sdk package imports this same
        // module from OSS (see kestra-ee/ui-ee/packages/kestra-sdk/openapi-ts.config.ts) and
        // client-sdk consumes the published build — instead of each forking its own copy.
        // `specPath` tells the plugin to stamp OPENAPI_SPEC_HASH into the generated SDK.
        defineConfigKestraHeyOptionalTenant({specPath}),
    ],
} satisfies UserConfig
