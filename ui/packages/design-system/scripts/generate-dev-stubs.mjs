/**
 * Generates stub files in dist/ that re-export from src/.
 * This keeps package.json exports stable while allowing live source editing during development.
 * Run automatically via the `prepare` script (local installs only, not when installed as a dependency).
 *
 * Component list is imported directly from componentEntries.ts — the same source of truth as tsdown.config.ts.
 * Requires Node 22+ (uses --experimental-strip-types to import the .ts file).
 */

// Skip during `npm publish` / `npm pack` — the real build (prepublishOnly) should own dist then.
if (["publish", "pack"].includes(process.env.npm_command)) {
    console.log("[dev-stubs] Skipping stub generation during publish/pack.")
    process.exit(0)
}

import {existsSync, readdirSync, mkdirSync, rmSync, writeFileSync} from "fs"
import {dirname, join, relative} from "path"
import {fileURLToPath} from "url"
import {componentEntries} from "../componentEntries.ts"
import {copyRecursive} from "./copy-recursive.mjs"

const __dirname = dirname(fileURLToPath(import.meta.url))
const packageRoot = join(__dirname, "..")

// if anything already exists in dist/, it should be kept for caching. 

// detect if anything in `dist` that is not the stubs.
const distDirGlobal = join(packageRoot, "dist")


if (existsSync(distDirGlobal) && readdirSync(distDirGlobal).length > 0) {
    const cacheFile = join(distDirGlobal, ".build-cache.json")
    if(existsSync(cacheFile)) {
        console.log("[dev-stubs] Existing dist/ content moved to dist/__cached__/ for caching.")
        const cachedDir = join(distDirGlobal, "__cached__")
        copyRecursive(distDirGlobal, cachedDir, {filter: (src) => !src.includes("__cached__")})
        // remove all non-stub files from dist since they got moved to cached
        for (const file of readdirSync(distDirGlobal)) {
            if (file === "__cached__") continue
            const filePath = join(distDirGlobal, file)
            if (existsSync(filePath)) {
                rmSync(filePath, {recursive: true, force: true})
            }
        }
    }
}


// componentEntries keys are like "components/Basic/KsButton/KsButton.vue" (relative to src/)
const entries = [
    ...Object.keys(componentEntries).map(srcKey => ({
        srcRelPath: "src/" + srcKey,                              // src/components/X/Y.vue
        distRelPath: "dist/" + srcKey.replace(/\.vue$/, ".js"),  // dist/components/X/Y.js
    })),
    // Fixed non-component entries (mirrors tsdown.config.ts entry: { index, styleBase })
    {srcRelPath: "src/index.ts",     distRelPath: "dist/index.js"},
    {srcRelPath: "src/styleBase.ts", distRelPath: "dist/styleBase.js"},
]

let created = 0

for (const {srcRelPath, distRelPath} of entries) {
    const distAbsPath = join(packageRoot, distRelPath)
    const distDir = dirname(distAbsPath)
    const srcAbsPath = join(packageRoot, srcRelPath)
    const relPath = relative(distDir, srcAbsPath).replace(/\\/g, "/")

    const isVue = srcRelPath.endsWith(".vue")
    const reexports = isVue || srcRelPath.endsWith("/index.ts")
        ? `export { default } from '${relPath}';\nexport * from '${relPath}';\n`
        : `export * from '${relPath}';\n`

    mkdirSync(distDir, {recursive: true})
    writeFileSync(distAbsPath, reexports, "utf-8")

    // .d.ts stub + .d.ts.map so "Go to Source Definition" jumps directly to the .vue/.ts file
    const dtsPath = distAbsPath.replace(/\.js$/, ".d.ts")
    const dtsFileName = dtsPath.split("/").pop()

    writeFileSync(dtsPath, reexports + `//# sourceMappingURL=${dtsFileName}.map\n`, "utf-8")
    writeFileSync(dtsPath + ".map", JSON.stringify({
        version: 3,
        file: dtsFileName,
        sourceRoot: "",
        sources: [relPath],
        mappings: "",
    }), "utf-8")

    created++
}

// write the color palette dev as a `@forward`
writeFileSync(join(distDirGlobal, ".stubs"), "", "utf-8") // marker file to indicate dist/ contains dev stubs

console.log(`[dev-stubs] Created ${created} stub(s).`)
