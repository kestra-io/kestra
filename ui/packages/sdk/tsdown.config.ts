import {defineConfig} from "tsdown"
import {readdirSync} from "fs"
import {join} from "path"

const sdkEntries = Object.fromEntries(
    readdirSync(join(import.meta.dirname, "src/openapi/sdk"))
        .filter(f => f.endsWith(".gen.ts"))
        .map(f => {
            // Strip "ks-" prefix and ".gen.ts" suffix: "ks-Outputs.gen.ts" → "outputs"
            const name = f.replace(/^ks-/, "").replace(/\.gen\.ts$/, "").replace(/ /g, "-").toLowerCase()
            return [name, `src/openapi/sdk/${f}`]
        })
)

const allEntries = {
    "index": "src/index.ts",
    "client": "src/openapi/client.gen.ts",
    ...sdkEntries,
}

export default defineConfig({
    platform: "browser",
    entry: allEntries,
    format: ["esm"],
    dts: {
        // Use tsc resolver so it respects tsconfig `moduleResolution: "bundler"`,
        // which resolves axios to index.d.ts (ESM, named exports) instead of
        // index.d.cts (CJS, export = axios), avoiding IMPORT_IS_UNDEFINED warnings.
        resolver: "tsc",
    },
    exports: true,
    clean: true,
})

