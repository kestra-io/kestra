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

export default
    allEntries.map((entry, i) => defineConfig({
        platform: "browser" as const,
        entry: [entry],
        format: ["esm"] as const,
        dts: true,
        clean: i === 0,
    }))

