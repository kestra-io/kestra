import path from "node:path"
import {readdirSync} from "node:fs"
import {defineConfig} from "tsdown"

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
        customExports(currentExports, {chunks, pkg}) {
            // Build a map: CSS basename → dist-relative path (e.g. "KsButton" → "./dist/KsButton.css")
            const pkgDir = pkg.packageJsonPath.replace(/\\/g, "/").replace(/\/package\.json$/, "")
            const cssByName = new Map<string, string>()
            for (const chunksByFormat of Object.values(chunks)) {
                for (const chunk of chunksByFormat) {
                    if (chunk.type === "asset" && (chunk.fileName as string).endsWith(".css")) {
                        const fileName = chunk.fileName as string
                        const outDir = (chunk.outDir as string).replace(/\\/g, "/")
                        const name = path.basename(fileName, ".css")
                        const relative = "./" + path.posix.relative(pkgDir, path.posix.join(outDir, fileName))
                        cssByName.set(name, relative)
                    }
                }
            }
            // Add one CSS export per component entry, keyed by its logical component path
            for (const key of Object.keys(componentEntries)) {
                const name = path.basename(key)
                const cssPath = cssByName.get(name)
                if (cssPath) currentExports[`./${key}.css`] = cssPath
            }
            // Add the global styles entry
            const stylesCss = cssByName.get("styleBase")
            if (stylesCss) currentExports["./styles.css"] = stylesCss
            return currentExports
        },
    },
    fromVite: true,
    hash: false,
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
        preprocessorOptions: {
            scss: {
                loadPaths: [path.resolve(import.meta.dirname, "../../node_modules")],
                silenceDeprecations: ["import", "color-functions", "global-builtin", "if-function"],
            },
        },
    },
})
