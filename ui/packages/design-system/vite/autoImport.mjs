// @ts-check

import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import Components from "unplugin-vue-components/vite"
import {ElementPlusResolver} from "unplugin-vue-components/resolvers"

const PACKAGE = "@kestra-io/design-system"

// This file's own location, so the barrel is found whether the caller is the app, the
// design system's own Storybook, or another workspace consuming the package.
const packageRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)))

// `import KsFoo from "./components/…"` in the barrel. The barrel is the package's own list
// of what it ships, so deriving the resolver from it means the two cannot drift: a
// component added there is auto-importable with no second list to keep in sync.
const BARREL_IMPORT = /^import\s+(Ks[A-Za-z0-9]*)\s+from\s+"\.\/(components\/[^"]+)"/gm

/**
 * Maps every component name the barrel imports to the deep specifier yielding just it.
 * @returns {Record<string, string>}
 */
function readComponentMap() {
    const barrel = path.join(packageRoot, "src/index.ts")
    /** @type {Record<string, string>} */
    const map = {}
    for (const [, name, subpath] of fs.readFileSync(barrel, "utf8").matchAll(BARREL_IMPORT)) {
        // The package's `./components/*` export substitutes the subpath literally, so the
        // extension TypeScript lets the barrel omit has to be put back here.
        map[name] = `${PACKAGE}/${subpath.endsWith(".vue") ? subpath : `${subpath}.ts`}`
    }
    if (!Object.keys(map).length) {
        throw new Error(`Design system auto-import found no components in ${barrel}. The barrel's import style likely changed.`)
    }
    return map
}

// Everything under node_modules except this package, whose components use each other by
// tag name and would otherwise stay unresolved when it is consumed as a linked workspace.
const EXCLUDE = new RegExp(`node_modules[\\\\/](?!${PACKAGE.replace("/", "[\\\\/]")}[\\\\/])`)

/**
 * Element Plus's own resolver, rewritten to import from `element-plus` rather than
 * `element-plus/es`. Both specifiers land on the same file, but every hand-written import
 * in the design system uses the former, and a second specifier for it means a second entry
 * for Vite to pre-bundle — discovered mid-run, which reloads the dev server.
 * @returns {import("unplugin-vue-components").ComponentResolver[]}
 */
function elementPlusResolver() {
    return ElementPlusResolver({importStyle: false}).map((resolver) => ({
        ...resolver,
        resolve: async (name) => {
            const resolved = await resolver.resolve(name)
            if (!resolved || typeof resolved === "string" || resolved.from !== "element-plus/es") return resolved
            return {...resolved, from: "element-plus"}
        },
    }))
}

/**
 * Resolves `<KsFoo>` in a template to a direct import of that component, replacing the
 * "register all components on the app" install the package used to ship.
 *
 * The point is bundle shape: a global install is one static reference to every component,
 * so the whole design system — and, through it, all of Element Plus — lands in the boot
 * chunk of every page. Per-file imports let the bundler keep only what a page renders.
 *
 * Every Vite config that compiles templates using `Ks*` tags needs this plugin: the app
 * build, the Vitest projects, and Storybook alike.
 * @returns {import("vite").Plugin}
 */
export function designSystemAutoImport() {
    const componentMap = readComponentMap()

    return Components({
        // Nothing to scan: components come from the resolvers, not a directory of local files.
        dirs: [],
        dts: false,
        // JSX compiles tag names to the same resolveComponent() calls a template does, so
        // stories written in JSX need the same treatment as .vue files.
        include: [/\.vue$/, /\.vue\?vue/, /\.[jt]sx$/],
        exclude: [EXCLUDE, /[\\/]\.git[\\/]/],
        resolvers: [
            (name) => componentMap[name],
            // `<el-*>` used directly, which the global Element Plus install used to cover.
            // Styles stay out of it: they come from the namespaced SCSS in assets/styles.
            elementPlusResolver(),
        ],
    })
}
