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
    exports: true,
    plugins: [svgInlinePlugin],
    fromVite: true,
    dts: {vue: true, tsconfig: "./tsconfig.app.json"},
    entry: {
        index: "src/index.ts",
        styleBase: "src/styleBase.ts",
        ...componentEntries,
    },
    copy: [
        {from: "src/assets/images", to: "dist/assets"},
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
