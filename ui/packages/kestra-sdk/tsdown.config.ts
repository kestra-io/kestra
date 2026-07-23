import {defineConfig} from "tsdown"
import {readdirSync} from "fs"
import {join} from "path"

// One bundle entry per generated per-tag SDK file, so consumers can deep-import
// `@kestra-io/kestra-sdk/executions`, `/flows`, … (the app relies on these subpaths).
export const sdkEntries = Object.fromEntries(
    readdirSync(join(import.meta.dirname, "src/openapi/sdk"))
        .filter(f => f.endsWith(".gen.ts"))
        .map(f => {
            // Strip ".gen.ts" suffix: "Outputs.gen.ts" → "outputs"
            const name = f.replace(/\.gen\.ts$/, "").replace(/([a-z])([A-Z])/g, "$1-$2").replace(/ /g, "-").toLowerCase()
            return [name, `src/openapi/sdk/${f}`]
        }),
)

export default defineConfig({
    platform: "browser",
    entry: {
        "index": "src/index.ts",
        "client": "src/openapi/client.gen.ts",
        ...sdkEntries,
    },
    format: ["esm"],
    // Bundle the shared runtime helper (createConfigureClient) into this package's dist so the SDK
    // stays self-contained — the only runtime import (@kestra-io/hey-api-plugin/runtime) is inlined,
    // so @kestra-io/hey-api-plugin remains a build/generation-time devDependency, not a runtime one.
    deps: {
        alwaysBundle: [/^@kestra-io\/hey-api-plugin/],
    },
    dts: {
        // Use the tsc resolver so it honors tsconfig `moduleResolution: "bundler"` when resolving the
        // bundled runtime's types.
        resolver: "tsc",
        sourcemap: true,
    },
    sourcemap: "hidden",
    // Rewrites this package.json's `exports` map from the entries above on every build. The committed
    // map is regenerated (and committed) whenever the SDK is regenerated; a plain build is idempotent.
    exports: true,
    clean: true,
})
