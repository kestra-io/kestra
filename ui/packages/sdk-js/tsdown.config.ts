import {defineConfig} from "tsdown"

export default defineConfig({
    entry: [
        "src/index.ts",
        "src/client.gen.ts",
        "src/sdk/*.gen.ts",
    ],
    format: ["esm"],
    dts: true,
    clean: true,
})
