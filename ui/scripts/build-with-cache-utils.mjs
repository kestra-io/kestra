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
import {existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync} from "node:fs"
import {join, relative} from "node:path"

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

export function computePackageHash(packageRoot, configFiles) {
    const absConfigFiles = configFiles
        .map(f => join(packageRoot, f))
        .filter(existsSync)

    const srcFiles = collectFilesRecursive(join(packageRoot, "src"))

    const allFiles = [...absConfigFiles, ...srcFiles].sort()

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

/**
 * 
 * @param {string} cacheFile 
 * @returns 
 */
export function readCache(cacheFile) {
    if (!existsSync(cacheFile)) return {package: null, entries: {}}
    try {
        const parsed = JSON.parse(readFileSync(cacheFile, "utf8"))
        return {package: null, entries: {}, ...parsed}
    } catch {
        return {package: null, entries: {}}
    }
}

export function writeCache(distDir, cacheFileName, cache) {
    mkdirSync(distDir, {recursive: true})
    const cacheFile = join(distDir, cacheFileName)
    writeFileSync(cacheFile, JSON.stringify(cache, null, 2) + "\n")
}
