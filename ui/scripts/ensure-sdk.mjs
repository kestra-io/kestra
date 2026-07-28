#!/usr/bin/env node
// @ts-check

// predev/prebuild/precheck:types hook; pass --check-spec for generate:sdk (the only Gradle path).
import {existsSync, readFileSync, readdirSync, statSync, writeFileSync} from "node:fs"
import {createHash} from "node:crypto"
import {execSync} from "node:child_process"
import path from "node:path"
import {fileURLToPath} from "node:url"

// This file's own location, not the caller's uiRoot: hey-api-plugin is a single physical package
// under kestra/ui/packages (both OSS's and EE's workspaces field point at the same directory), so
// its path must not depend on whether OSS or EE invoked us.
const ownUiRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const heyApiPluginDir = path.join(ownUiRoot, "packages/hey-api-plugin")

/** @param {string} cmd @param {string} uiRoot */
export function run(cmd, uiRoot) {
    execSync(cmd, {stdio: "inherit", cwd: uiRoot})
}


/**
 * Hashes file paths + contents (not mtimes, which git checkout/rebase don't preserve meaningfully).
 * Each input may be a file or a directory (hashed recursively); combined into a single digest. 
 * @param {string[]} inputs 
 * @returns {string} 
 */
function hashInputs(inputs) {
    const hash = createHash("sha256")
    /** @type {Array<[string, string]>} */
    const files = []
    /** @param {string} base @param {string} p */
    const walk = (base, p) => {
        if (statSync(p).isDirectory()) {
            for (const entry of readdirSync(p, {withFileTypes: true}).sort((a, b) => a.name.localeCompare(b.name))) {
                walk(base, path.join(p, entry.name))
            }
        } else {
            files.push([path.relative(base, p), p])
        }
    }
    for (const input of inputs) walk(path.dirname(input), input)
    for (const [rel, file] of files) {
        hash.update(rel)
        hash.update(readFileSync(file))
    }
    return hash.digest("hex")
}

/** @param {string[]} inputs @param {string} hashFile @returns {boolean} */
function isStale(inputs, hashFile) {
    return !existsSync(hashFile) || readFileSync(hashFile, "utf8") !== hashInputs(inputs)
}

/** @param {string[]} inputs @param {string} hashFile */
function recordHash(inputs, hashFile) {
    writeFileSync(hashFile, hashInputs(inputs))
}


/**
 * Runs `npm install` in uiRoot when its node_modules is out of sync with package-lock.json —
 * not just missing, but stale: node_modules can exist yet no longer match a lockfile that changed
 * since the last install (e.g. a devDependency bump in hey-api-plugin's package.json), which is
 * exactly the case a plain existsSync(node_modules) check would miss. Content-hashed, not mtime
 * based, for the same reason as hashInputs above.
 * @param {string} uiRoot 
 */
function ensureDepsInstalled(uiRoot) {
    const lockFile = path.join(uiRoot, "package-lock.json")
    if (!existsSync(lockFile)) return
    const marker = path.join(uiRoot, "node_modules/.ensure-sdk-lock-hash")
    if (existsSync(path.join(uiRoot, "node_modules")) && !isStale([lockFile], marker)) return

    console.log(`[ensure-sdk] Dependencies in ${uiRoot} are missing or out of sync with package-lock.json — running npm install.`)
    run("npm install", uiRoot)
    recordHash([lockFile], marker)
}


/**
 * Bundles the committed src/openapi into dist/ when missing, or when either package's tracked
 * sources changed since the last build; never touches Gradle or regenerates code.
 *
 * kestra-sdk bundles @kestra-io/hey-api-plugin into its own dist at build time (tsdown noExternal),
 * so kestra-sdk's own staleness check tracks hey-api-plugin's built dist (its actual build input,
 * not its src) alongside kestra-sdk's own src + package.json (a dependency bump also needs a rebuild).
 * @param {string} uiRoot */
export function ensureSdkBundled(uiRoot) {
    const sdkDir = path.join(uiRoot, "packages/kestra-sdk")
    const sdkDist = path.join(sdkDir, "dist/index.js")
    const pluginHashFile = path.join(heyApiPluginDir, "dist/.src-hash")
    const sdkHashFile = path.join(sdkDir, "dist/.src-hash")

    const pluginStale = isStale([path.join(heyApiPluginDir, "src")], pluginHashFile)
    if(pluginStale){
        console.log("[kestra-sdk] Source changed since the last build (hey-api-plugin) — rebuilding.")
        run("npm run build --workspace=@kestra-io/hey-api-plugin", uiRoot)
    }
    const sdkStale = !existsSync(sdkDist) || pluginStale || isStale(
        [path.join(sdkDir, "src"), path.join(sdkDir, "package.json"), path.join(heyApiPluginDir, "dist")],
        sdkHashFile,
    )
    if (!sdkStale) return

    ensureDepsInstalled(uiRoot)
    console.log(existsSync(sdkDist)
        ? "[kestra-sdk] Source changed since the last build (kestra-sdk) — rebuilding."
        : "[kestra-sdk] No build found — bundling the committed SDK source once (subsequent runs are instant).")
    try {
        run("npm run build --workspace=@kestra-io/kestra-sdk", uiRoot)
    } catch {
        console.error("[kestra-sdk] Building the committed SDK source failed (see above).")
        process.exit(1)
    }

    if (!existsSync(sdkDist)) {
        console.error("[kestra-sdk] Build reported success but no dist output was produced.")
        process.exit(1)
    }
    recordHash([path.join(heyApiPluginDir, "src")], pluginHashFile)
    recordHash([path.join(sdkDir, "src"), path.join(sdkDir, "package.json"), path.join(heyApiPluginDir, "dist")], sdkHashFile)
}


/**
 * Regenerates the SDK from openapi.yml, skipping the expensive steps when OPENAPI_SPEC_HASH matches. 
 * @param {string} uiRoot 
 */
export function checkSpecAndGenerate(uiRoot) {
    const specPath = path.join(uiRoot, "../openapi.yml")
    const sharedGenPath = path.join(uiRoot, "packages/kestra-sdk/src/openapi/sdk/shared.gen.ts")

    console.log("[generate:sdk] Generating openapi.yml via Gradle...")
    run("npm run generate:openapi-spec", uiRoot)

    const specHash = createHash("sha256").update(readFileSync(specPath)).digest("hex").slice(0, 16)
    const committedHash = existsSync(sharedGenPath)
        ? /OPENAPI_SPEC_HASH = '([0-9a-f]+)'/.exec(readFileSync(sharedGenPath, "utf8"))?.[1]
        : undefined

    if (committedHash === specHash) {
        console.log(`[generate:sdk] Spec unchanged (hash ${specHash}) — committed src/openapi is already up to date, skipping regeneration.`)
        return ensureSdkBundled(uiRoot)
    }

    console.log(committedHash
        ? `[generate:sdk] Spec changed (${committedHash} -> ${specHash}) — regenerating.`
        : "[generate:sdk] No committed hash found — regenerating.")

    ensureDepsInstalled(uiRoot)
    run("npm run build --workspace=@kestra-io/hey-api-plugin", uiRoot)
    run("npm run generate:openapi --workspace=@kestra-io/kestra-sdk", uiRoot)
    run("npm run build --workspace=@kestra-io/kestra-sdk", uiRoot)

    const sdkDir = path.join(uiRoot, "packages/kestra-sdk")
    recordHash([path.join(heyApiPluginDir, "src")], path.join(heyApiPluginDir, "dist/.src-hash"))
    recordHash([path.join(sdkDir, "src"), path.join(sdkDir, "package.json"), path.join(heyApiPluginDir, "dist")], path.join(sdkDir, "dist/.src-hash"))
}

// Only run as CLI when executed directly — not when EE's ensure-sdk.mjs imports these functions.
if (import.meta.url === `file://${process.argv[1]}`) {
    const uiRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
    if (process.argv.includes("--check-spec")) {
        checkSpecAndGenerate(uiRoot)
    } else {
        ensureSdkBundled(uiRoot)
    }
}
