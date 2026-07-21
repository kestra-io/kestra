import { defineConfig } from "tsdown"

export default defineConfig({
    platform: "neutral",
    entry: {
        // Generation-time: the @hey-api/openapi-ts plugin.
        index: "src/index.ts",
        // Runtime: createConfigureClient — the universal axios setup shipped in every SDK.
        runtime: "src/runtime.ts",
    },
    format: ["esm"],
    // Keep @hey-api/* and axios external so the dts bundler does not descend into their (CommonJS)
    // transitive type deps (e.g. @types/semver), which rolldown-plugin-dts cannot bundle. Consumers
    // provide these (optional peer dependencies), so the emitted d.ts referencing them resolves.
    deps: {
        neverBundle: [/^@hey-api\//, "axios"],
    },
    dts: true,
    clean: true,
})
