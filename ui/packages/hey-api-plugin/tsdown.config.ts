import {defineConfig} from "tsdown"

// This package ships two entries that target DIFFERENT environments, so each is built with its own
// platform:
//  - "."        (index)   — the @hey-api/openapi-ts codegen plugin, run under Node during generation.
//  - "./runtime"(runtime) — createConfigureClient, the fetch setup that runs in the browser.
// Node emits .mjs/.d.mts, browser emits .js/.d.ts — the package.json `exports` map points each
// subpath at its actual file accordingly.
export default defineConfig([
    {
        entry: {index: "src/index.ts"},
        platform: "node",
        format: ["esm"],
        dts: true,
        // Clean dist once, here on the first config; the browser config below must NOT clean or it
        // would wipe this output.
        clean: true,
        deps: {
            // Keep external:
            //  - node built-ins (node:crypto/node:fs) — imported directly by the codegen entry.
            //  - @hey-api/* — so the dts bundler does not descend into their (CommonJS) transitive
            //    type deps (e.g. @types/semver), which rolldown-plugin-dts cannot bundle. Consumers
            //    provide @hey-api/openapi-ts (optional peer), so the emitted d.ts still resolves.
            neverBundle: [/^node:/, /^@hey-api\//],
        },
    },
    {
        entry: {runtime: "src/runtime.ts"},
        platform: "browser",
        format: ["esm"],
        dts: true,
        clean: false,
    },
])
