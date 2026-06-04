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

import {createHash} from "node:crypto"
import {existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync, rmSync} from "node:fs"
import {join, relative, resolve} from "node:path"
import * as tsdown from "tsdown"
import tsdownConfig from "../tsdown.config.ts"
import {copyRecursive} from "./copy-recursive.mjs"

const packageRoot = resolve(import.meta.dirname, "..")
const distDir = join(packageRoot, "dist")
const cacheFile = join(distDir, ".build-cache.json")

const force = process.argv.includes("--force")

// ---------------------------------------------------------------------------
// File collection
// ---------------------------------------------------------------------------

function collectFilesRecursive(dir) {
    const files = []
    if (!existsSync(dir)) return files
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) {
            files.push(...collectFilesRecursive(full))
        } else {
            files.push(full)
        }
    }
    return files
}

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

function computePackageHash() {
    const configFiles = CONFIG_FILES
        .map(f => join(packageRoot, f))
        .filter(existsSync)

    const srcFiles = collectFilesRecursive(join(packageRoot, "src"))

    const allFiles = [...configFiles, ...srcFiles].sort()

    const hash = createHash("sha256")
    for (const file of allFiles) {
        // Include the relative path so renames also invalidate the cache.
        hash.update(relative(packageRoot, file))
        hash.update("\0")
        hash.update(readFileSync(file))
        hash.update("\0")
    }
    return hash.digest("hex")
}

// ---------------------------------------------------------------------------
// Cache persistence
// ---------------------------------------------------------------------------

function readCache() {
    if (!existsSync(cacheFile)) return {package: null, entries: {}}
    try {
        const parsed = JSON.parse(readFileSync(cacheFile, "utf8"))
        return {package: null, entries: {}, ...parsed}
    } catch {
        return {package: null, entries: {}}
    }
}

function writeCache(cache) {
    mkdirSync(distDir, {recursive: true})
    writeFileSync(cacheFile, JSON.stringify(cache, null, 2) + "\n")
}

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

const packageHash = computePackageHash()
const cache = readCache()

if (!force && cache.package === packageHash) {
    console.log("Build cache hit — no source changes detected, skipping build.")
    process.exit(0)
}

if (force) {
    console.log("--force flag set, rebuilding...")
} else {
    console.log("Source changes detected, building...")
}

await tsdown.build(tsdownConfig)

writeCache({...cache, package: packageHash})
