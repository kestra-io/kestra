import path from "node:path"
import {readFileSync} from "node:fs"
import {defineConfig} from "tsdown"
import {componentEntries} from "./componentEntries.ts"

const svgInlinePlugin = {
    name: "svg-inline",
    load(id: string) {
        if (id.endsWith(".svg")) {
            const base64 = readFileSync(id).toString("base64")
            return `export default "data:image/svg+xml;base64,${base64}"`
        }
    },
}



export default defineConfig({
    platform: "browser",
    exports: {
        customExports(exp) {
            exp["./color-palette.scss"] = "./dist/_color-palette.scss"
            return exp
        },
    },
    dts: {vue: true, tsconfig: "./tsconfig.app.json"},
    plugins: [svgInlinePlugin],
    fromVite: true,
    entry: {
        index: "src/index.ts",
        styleBase: "src/styleBase.ts",
        ...componentEntries,
    },
    copy: [
        {from: "src/assets/images", to: "dist/assets"},
        {from: "src/assets/styles/_color-palette.scss", to: "dist"},
    ],
    deps: {
        neverBundle: [/\.png$/, "@vue/reactivity"],
    },
    css: {
        splitting: true,
        inject: true,
        preprocessorOptions: {
            scss: {
                loadPaths: [path.resolve(import.meta.dirname, "../../node_modules")],
                silenceDeprecations: ["import", "color-functions", "global-builtin", "if-function"],
            },
        },
    },
})
