#!/usr/bin/env node
// Runs as ui/package.json's generate:sdk script — the only path that invokes Gradle.
//
// Full regeneration (build the shared hey-api-plugin, run openapi-ts, bundle dist/) is expensive,
// but the OpenAPI spec often hasn't actually changed between runs. The generated SDK already stamps
// OPENAPI_SPEC_HASH = sha256(openapi.yml)[:16] into packages/kestra-sdk/src/openapi/sdk/shared.gen.ts
// (see packages/hey-api-plugin/src/plugin.ts) for the dev-time staleness check. Reuse that same hash
// here as a build cache key: after Gradle regenerates openapi.yml, hash it the same way and compare
// against the committed value. Equal hash means the committed src/openapi is already correct for the
// current spec, so skip straight past the expensive steps (only bundling dist/ if that's missing).
import {existsSync, readFileSync} from "node:fs"
import {createHash} from "node:crypto"
import {execSync} from "node:child_process"
import path from "node:path"
import {fileURLToPath} from "node:url"

const uiRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const specPath = path.join(uiRoot, "../openapi.yml")
const sharedGenPath = path.join(uiRoot, "packages/kestra-sdk/src/openapi/sdk/shared.gen.ts")
const sdkDist = path.join(uiRoot, "packages/kestra-sdk/dist/index.js")

function run(cmd) {
    execSync(cmd, {stdio: "inherit", cwd: uiRoot})
}

console.log("[generate:sdk] Generating openapi.yml via Gradle...")
run("npm run generate:openapi-spec")

const specHash = createHash("sha256").update(readFileSync(specPath)).digest("hex").slice(0, 16)

const committedHash = existsSync(sharedGenPath)
    ? /OPENAPI_SPEC_HASH = '([0-9a-f]+)'/.exec(readFileSync(sharedGenPath, "utf8"))?.[1]
    : undefined

if (committedHash === specHash) {
    console.log(`[generate:sdk] Spec unchanged (hash ${specHash}) — committed src/openapi is already up to date, skipping regeneration.`)
    if (!existsSync(sdkDist)) {
        console.log("[generate:sdk] No dist/ build found — bundling the committed source.")
        run("npm run build:sdk")
    }
    process.exit(0)
}

console.log(committedHash
    ? `[generate:sdk] Spec changed (${committedHash} -> ${specHash}) — regenerating.`
    : "[generate:sdk] No committed hash found — regenerating.")

run("npm run build --workspace=@kestra-io/hey-api-plugin")
run("npm run generate:openapi --workspace=@kestra-io/kestra-sdk")
run("npm run build:sdk")
