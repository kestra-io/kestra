import { defineConfig } from "tsdown"

export default defineConfig({
    platform: "node",
    entry: {
        index: "src/index.ts",
    },
    format: ["esm"],
    // Keep @hey-api/openapi-ts external so the dts bundler does not descend into its (CommonJS)
    // transitive type deps like @types/semver, which rolldown-plugin-dts cannot bundle. Consumers
    // already have @hey-api/openapi-ts (peer dependency), so the emitted d.ts referencing it resolves.
    deps: {
        neverBundle: [/^@hey-api\//],
    },
    dts: true,
    clean: true,
})
