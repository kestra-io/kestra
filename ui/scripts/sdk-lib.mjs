// Shared logic for ensure-sdk.mjs / generate-sdk.mjs. EE reuses this file by relative import
// (see ui-ee/scripts/*.mjs) instead of duplicating it, the same way its SDK reuses dev-freshness.ts.
import {existsSync, readFileSync} from "node:fs"
import {createHash} from "node:crypto"
import {execSync} from "node:child_process"
import path from "node:path"

export function run(cmd, cwd) {
    execSync(cmd, {stdio: "inherit", cwd})
}

// Bundles the committed src/openapi into dist/ when missing; never touches Gradle or regenerates code.
export function ensureSdkBundled(uiRoot) {
    const sdkDist = path.join(uiRoot, "packages/kestra-sdk/dist/index.js")
    if (existsSync(sdkDist)) return

    console.log("[kestra-sdk] No build found — bundling the committed SDK source once (subsequent runs are instant).")
    try {
        run("npm run build:sdk", uiRoot)
    } catch {
        console.error("[kestra-sdk] Building the committed SDK source failed (see above).")
        process.exit(1)
    }

    if (!existsSync(sdkDist)) {
        console.error("[kestra-sdk] Build reported success but no dist output was produced.")
        process.exit(1)
    }
}

// Regenerates the SDK from openapi.yml, skipping the expensive steps when OPENAPI_SPEC_HASH matches.
export function generateSdk(uiRoot) {
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
        ensureSdkBundled(uiRoot)
        return
    }

    console.log(committedHash
        ? `[generate:sdk] Spec changed (${committedHash} -> ${specHash}) — regenerating.`
        : "[generate:sdk] No committed hash found — regenerating.")

    run("npm run build --workspace=@kestra-io/hey-api-plugin", uiRoot)
    run("npm run generate:openapi --workspace=@kestra-io/kestra-sdk", uiRoot)
    run("npm run build:sdk", uiRoot)
}
