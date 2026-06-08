/**
 * Hash-based build cache for the design-system package.
 *
 * Package-level (current):
 *   Hashes all source files under src/ plus key config files.
 *   If the hash matches the stored value in dist/.build-cache.json the build
 *   is skipped entirely.
 *
 * Entry-level (future):
 *   The cache file uses a `entries` map keyed by entry name so that individual
 *   component entries can be skipped when only unrelated files changed.
 *
 * Usage:
 *   node scripts/build-with-cache.mjs           # normal (cached)
 *   node scripts/build-with-cache.mjs --force   # bypass cache, always build
 */

import {existsSync, readdirSync, rmSync} from "node:fs"
import {join, resolve} from "node:path"
import * as tsdown from "tsdown"
import tsdownConfig from "../tsdown.config.ts"
import {copyRecursive} from "./copy-recursive.mjs"
import {readCache, writeCache, computePackageHash} from "../../../scripts/build-with-cache-utils.mjs"

const packageRoot = resolve(import.meta.dirname, "..")
const distDir = join(packageRoot, "dist")
const cacheFile = join(distDir, ".build-cache.json")

const force = process.argv.includes("--force")

// ---------------------------------------------------------------------------
// Hashing
// ---------------------------------------------------------------------------

const CONFIG_FILES = [
    "componentEntries.ts",
    "tsdown.config.ts",
    "tsconfig.app.json",
    "tsconfig.json",
    "package.json",
]

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

// if dist/.stubs exists, it means the dev stub generation script ran and populated dist/ with stubs and move the built files to dist/cached. 
// In that case, we should delete all files here except dist/cached where the real cached build is stored.
// Then move the `dist/cached` directory contents to `dist/` so that the real build files are in place for the cache check and potential reuse.
const stubsMarker = join(distDir, ".stubs")
if (existsSync(stubsMarker)) {
    console.log("Dev stubs detected in dist/, clearing stubs and restoring cached build if available...")
    for (const file of readdirSync(distDir)) {
        if (file === "__cached__") continue
        const filePath = join(distDir, file)
        if (existsSync(filePath)) {
            // remove stub file or directory
            rmSync(filePath, {recursive: true, force: true})
        }
    }
    const cachedDir = join(distDir, "__cached__")
    if (existsSync(cachedDir)) {
        copyRecursive(cachedDir, distDir)
        // remove cached directory after restoring
        rmSync(cachedDir, {recursive: true, force: true})
    }
}

const packageHash = computePackageHash(packageRoot, CONFIG_FILES)
const cache = readCache(cacheFile)

if (!force && cache.package === packageHash) {
    console.log("[Design System] Build cache hit — no source changes detected, skipping build.")
    process.exit(0)
}

if (force) {
    console.log("[Design System] --force flag set, rebuilding...")
} else {
    console.log("[Design System] Source changes detected, building...")
}

await tsdown.build(tsdownConfig)

writeCache(distDir, ".build-cache.json", {...cache, package: packageHash})
