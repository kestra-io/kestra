import {defineConfig} from "tsdown"

export default defineConfig({
    entry: {
        index: "src/index.ts",
        "vue-flow-utils": "src/vue-flow-utils.ts",
    },
    platform: "browser",
    exports: {
        enabled: "ci-only",
        customExports: {"./standalone.css": "./dist/standalone.css"},
    },
    copy: [{from: "src/standalone.css", to: "dist"}],
    fromVite: true,
    dts: {vue: true},
    deps: {
        neverBundle: ["@vue/reactivity"],
    },
})
