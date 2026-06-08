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

import {join, resolve} from "node:path"
import * as vite from "vite"
import {readCache, writeCache, computePackageHash} from "./build-with-cache-utils.mjs"

const packageRoot = resolve(import.meta.dirname, "..")
const distDir = join(packageRoot, "dist")
const cacheFile = join(distDir, ".build-cache.json")

const force = process.argv.includes("--force")

const packageHash = computePackageHash(packageRoot, [
    "vite.config.js",
    "tsconfig.base.json",
    "tsconfig.app.json",
    "tsconfig.json",
    "package.json",
    "packages/design-system/dist/.build-cache.json", // include the cache file itself so that changes to it invalidate the cache
])

const contractsHash = computePackageHash(join(packageRoot, "packages", "slot-contracts"), [
    "tsdown.config.ts",
    "tsconfig.json",
    "package.json",
])

const topologyHash = computePackageHash(join(packageRoot, "packages", "topology"), [
    "vite.config.ts",
    "tsdown.config.ts",
    "tsconfig.json",
    "package.json",
])

const hashComplete = `${packageHash}:${contractsHash}:${topologyHash}`

const cache = readCache(cacheFile)

if (!force && cache.package === hashComplete) {
    console.log("[UI] Build cache hit — no source changes detected, skipping build.")
    process.exit(0)
}

if (force) {
    console.log("[UI] --force flag set, rebuilding...")
} else {
    console.log("[UI] Source changes detected, building...")
}

await vite.build({
    configFile: join(packageRoot, "vite.config.js"),
})

writeCache(distDir, ".build-cache.json", {...cache, package: hashComplete})
