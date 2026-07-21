import { defineConfig } from "tsdown"

export default defineConfig({
    platform: "node",
    entry: {
        index: "src/index.ts",
    },
    format: ["esm"],
    dts: {
        resolver: "tsc",
    },
    clean: true,
})
