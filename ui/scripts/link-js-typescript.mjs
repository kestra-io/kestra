/**
 * typescript@7 ships only the native (Go) compiler — it no longer exposes the JS
 * compiler API that several tools still load via `require("typescript")`:
 *
 * - @typescript-eslint (parsing TS sources for ESLint; peer range is still <7)
 * - vue/compiler-sfc's register-ts.js (resolving imported types in defineProps<T>()
 *   during the Vite build)
 *
 * Until those tools support TypeScript 7, we install typescript@6 under the alias
 * "typescript6" and link it as `typescript` inside each consuming package, so
 * `require("typescript")` resolves to the JS implementation there while the rest
 * of the repo uses typescript@7.
 *
 * Runs from the package root via postinstall. Delete this script (and the
 * "typescript6" dependency + the typescript overrides in package.json) once the
 * consumers below are compatible with TypeScript 7.
 */
import {existsSync, mkdirSync, rmSync, symlinkSync} from "node:fs"
import {dirname, join, relative} from "node:path"

const root = process.cwd()
const typescript6 = join(root, "node_modules", "typescript6")

const consumers = [
    "node_modules/@typescript-eslint/parser",
    "node_modules/@typescript-eslint/typescript-estree",
    "node_modules/@typescript-eslint/project-service",
    "node_modules/@typescript-eslint/tsconfig-utils",
    "node_modules/ts-api-utils",
    "node_modules/vue",
]

if (!existsSync(typescript6)) {
    console.error("link-js-typescript: node_modules/typescript6 not found — did npm install run?")
    process.exit(1)
}

for (const consumer of consumers) {
    const pkgDir = join(root, consumer)
    if (!existsSync(pkgDir)) continue
    const linkPath = join(pkgDir, "node_modules", "typescript")
    mkdirSync(dirname(linkPath), {recursive: true})
    rmSync(linkPath, {recursive: true, force: true})
    const target = relative(dirname(linkPath), typescript6)
    try {
        symlinkSync(target, linkPath, "dir")
    } catch {
        // Windows without developer mode: junctions need no privilege but require absolute paths
        symlinkSync(typescript6, linkPath, "junction")
    }
}
