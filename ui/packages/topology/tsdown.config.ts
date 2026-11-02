import {defineConfig} from "tsdown"

export default defineConfig({
    entry: {
        index: "src/index.ts",
        "vue-flow-utils": "src/vue-flow-utils.ts",
    },
    platform: "browser",
    exports: "ci-only",
    fromVite: true,
    dts: {vue: true},
    deps: {
        neverBundle: ["@vue/reactivity"],
    },
})
