import {defineConfig} from "tsdown"
import {readdirSync} from "fs"
import {join} from "path"

const sdkEntries = readdirSync(join(import.meta.dirname, "src/sdk"))
    .filter(f => f.endsWith(".gen.ts"))
    .map(f => `src/sdk/${f}`)

const allEntries = [
    "src/index.ts",
    "src/client.gen.ts",
    ...sdkEntries,
]

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

