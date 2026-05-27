import path from "node:path"
import {readdirSync, readFileSync} from "node:fs"
import {defineConfig} from "tsdown"

const svgInlinePlugin = {
    name: "svg-inline",
    load(id: string) {
        if (id.endsWith(".svg")) {
            const base64 = readFileSync(id).toString("base64")
            return `export default "data:image/svg+xml;base64,${base64}"`
        }
    },
}

function findVueFiles(dir: string): string[] {
    const results: string[] = []
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        if (entry.isDirectory()) {
            results.push(...findVueFiles(path.join(dir, entry.name)))
        } else if (entry.name.endsWith(".vue")) {
            results.push(path.join(dir, entry.name))
        }
    }
    return results
}

const componentsDir = path.resolve(import.meta.dirname, "src/components")
const componentEntries = Object.fromEntries(
    findVueFiles(componentsDir).map(file => {
        const key = path.relative(path.resolve(import.meta.dirname, "src"), file).replace(/\.vue$/, "")
        return [key, "./" + path.relative(import.meta.dirname, file).replace(/\\/g, "/")]
    }),
)

export default defineConfig({
    platform: "browser",
    exports: {
        enabled: "ci-only",
        devExports: false,
    },
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
