import {defineConfig} from "tsdown"

export default defineConfig({
    // Neutral platform: the codegen entry runs under Node during generation; the runtime entry runs
    // in the browser. Neither should get a platform-specific shim baked in.
    platform: "neutral",
    entry: {
        // Generation-time: the @hey-api/openapi-ts plugin (the "." export).
        index: "src/index.ts",
        // Runtime: createConfigureClient — the universal fetch setup shipped in every SDK (the
        // "./runtime" export). Consumers bundle this into their SDK dist via noExternal.
        runtime: "src/runtime.ts",
    },
    format: ["esm"],
    // Keep @hey-api/* external so the dts bundler does not descend into their (CommonJS) transitive
    // type deps (e.g. @types/semver), which rolldown-plugin-dts cannot bundle. The codegen entry's
    // consumers provide @hey-api/openapi-ts (optional peer), so the emitted d.ts referencing it
    // resolves. The runtime entry imports nothing external, so this only affects the codegen dts.
    deps: {
        neverBundle: [/^@hey-api\//],
    },
    dts: true,
    clean: true,
})
