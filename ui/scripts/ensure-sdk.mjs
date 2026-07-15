#!/usr/bin/env node
// Runs as ui/package.json's predev/prebuild hook. Goal: `npm run dev` stays instant on repeat
// runs, and never crashes with a raw stack trace when Gradle can't run in this environment
// (e.g. no network access to download the Gradle distribution).
//
// - If the SDK is already built, do nothing — this is the hot path for every dev-server
//   restart after the first. Run `npm run generate:sdk` explicitly to force a refresh after
//   changing OSS backend code.
// - Otherwise, try to generate the OSS-only spec via Gradle, then build the SDK from it.
//   A Gradle failure is reported clearly and does not crash the script by itself — only the
//   final "is there actually a build?" check decides whether predev fails.
import { existsSync } from "node:fs";
import { execSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const uiRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const sdkDist = path.join(uiRoot, "packages/kestra-sdk/dist/index.js");
const specPath = path.join(uiRoot, "..", "openapi.yml");

if (existsSync(sdkDist)) {
    process.exit(0);
}

console.log("[kestra-sdk] No build found — generating once (subsequent `npm run dev` will be instant).");

let specExists = existsSync(specPath);
if (!specExists) {
    try {
        execSync("npm run generate:openapi-spec", { stdio: "inherit" });
        specExists = existsSync(specPath);
    } catch {
        console.warn(
            "\n[kestra-sdk] Could not generate the OpenAPI spec via Gradle (see above) — " +
            "this environment likely can't run ./gradlew (e.g. no network access to fetch the " +
            "Gradle distribution). Run `npm run generate:openapi-spec` manually once Gradle is " +
            "available here, or copy an existing openapi.yml to the repo root.\n",
        );
    }
}

if (!specExists) {
    console.error("[kestra-sdk] No openapi.yml and Gradle couldn't produce one — cannot build the SDK.");
    process.exit(1);
}

try {
    execSync("npm run build:sdk", { stdio: "inherit" });
} catch {
    console.error("[kestra-sdk] openapi.yml exists but building the SDK package failed (see above).");
    process.exit(1);
}

if (!existsSync(sdkDist)) {
    console.error("[kestra-sdk] Build reported success but no dist output was produced.");
    process.exit(1);
}
