import { defineConfig } from "tsdown"
import { readdirSync } from "fs"
import { join } from "path"

export const sdkEntries = Object.fromEntries(
    readdirSync(join(import.meta.dirname, "src/openapi/sdk"))
        .filter(f => f.endsWith(".gen.ts"))
        .map(f => {
            // Strip ".gen.ts" suffix: "Outputs.gen.ts" → "outputs"
            const name = f.replace(/\.gen\.ts$/, "").replace(/([a-z])([A-Z])/g, "$1-$2").replace(/ /g, "-").toLowerCase()
            return [name, `src/openapi/sdk/${f}`]
        })
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
    // stays self-contained — no runtime dependency on @kestra-io/hey-api-plugin (it is only a
    // build/generation-time devDependency).
    noExternal: [/^@kestra-io\/hey-api-plugin/],
    dts: {
        // Use tsc resolver so it respects tsconfig `moduleResolution: "bundler"`,
        // which resolves axios to index.d.ts (ESM, named exports) instead of
        // index.d.cts (CJS, export = axios), avoiding IMPORT_IS_UNDEFINED warnings.
        resolver: "tsc",
        sourcemap: true,
    },
    sourcemap: "hidden",
    exports: true,
    clean: true,
})
