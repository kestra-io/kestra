import {defineConfig} from "tsdown"
import {generateFlatDts} from "./scripts/generate-flat-dts.ts"

export default defineConfig({
    entry: {
        index: "src/index.ts",
    },
    platform: "browser",
    exports: "ci-only",
    dts: true,
    plugins: [{
        name: "slot-contracts:flat-dts",
        async generateBundle(_opts, bundle) {
            for (const [name, chunk] of Object.entries(bundle)) {
                if (name.endsWith(".d.ts") && chunk.type === "chunk") {
                    chunk.code = await generateFlatDts()
                }
            }
        },
    }],
})
