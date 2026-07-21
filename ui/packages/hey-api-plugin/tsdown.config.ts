import {defineConfig} from "tsdown"

export default defineConfig({
    // Neutral platform: this package's two entries target DIFFERENT environments — the "." codegen
    // entry runs under Node (and imports node:crypto/node:fs for the spec-hash stamp), while the
    // "./runtime" entry runs in the browser. Neutral forces neither node nor browser onto either
    // entry; we just declare the codegen entry's node built-ins as external below.
    platform: "neutral",
    entry: {
        // Generation-time (Node): the @hey-api/openapi-ts plugin (the "." export).
        index: "src/index.ts",
        // Runtime (browser): createConfigureClient — the universal fetch setup shipped in every SDK
        // (the "./runtime" export). Consumers bundle this into their SDK dist via deps.alwaysBundle.
        runtime: "src/runtime.ts",
    },
    format: ["esm"],
    deps: {
        // Never bundle (i.e. keep external):
        //  - node built-ins (node:crypto/node:fs) — the codegen entry runs in Node and imports them
        //    directly; declaring them here avoids the "could not resolve" warning under neutral.
        //  - @hey-api/* — so the dts bundler does not descend into their (CommonJS) transitive type
        //    deps (e.g. @types/semver), which rolldown-plugin-dts cannot bundle. The codegen entry's
        //    consumers provide @hey-api/openapi-ts (optional peer), so the emitted d.ts resolves.
        neverBundle: [/^node:/, /^@hey-api\//],
    },
    dts: true,
    clean: true,
})
