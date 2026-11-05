import {defineConfig} from "tsdown"

export default defineConfig({
    entry: {
        index: "src/index.ts",
        "expressive-code": "src/expressive-code.ts",
    },
    platform: "neutral",
    dts: true,
    exports: {
        enabled: "ci-only",
        customExports: {
            "./monaco/*": {types: "./dist/monaco/*.d.ts", default: "./dist/monaco/*.js"},
            "./shiki/*": {types: "./dist/shiki/*.d.ts", default: "./dist/shiki/*.js"},
            "./css/*": "./dist/css/*",
            "./vscode/*": "./dist/vscode/*",
        },
    },
    onSuccess: "node scripts/generate.ts",
})
