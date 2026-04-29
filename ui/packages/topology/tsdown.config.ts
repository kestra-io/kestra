import {defineConfig} from "tsdown"

export default defineConfig({
    platform: "browser",
    exports: "ci-only",
    fromVite: true,
    dts: {vue: true},
})
