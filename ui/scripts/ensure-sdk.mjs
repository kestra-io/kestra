#!/usr/bin/env node
// Runs as ui/package.json's predev / prebuild / precheck:types hook.
//
// The generated SDK source (ui/packages/kestra-sdk/src/openapi) is COMMITTED to git, so this
// script never generates code and never touches Gradle — it only bundles the committed source
// into dist/ when that build is missing. This is what decouples the fast (npm) CI from the
// Gradle/backend build: `npm run dev`, `build`, and `check:types` all work with no Java toolchain.
//
// - If dist is already present, do nothing (the hot path — instant on every repeat run).
// - Otherwise build the SDK package from its committed source.
//
// To refresh the SDK after the OSS API changes, run `npm run generate:sdk` explicitly (that is the
// only path that invokes Gradle) — or let the `sdk-drift-check` CI workflow do it.
import { existsSync } from "node:fs";
import { execSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const uiRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const sdkDist = path.join(uiRoot, "packages/kestra-sdk/dist/index.js");

if (existsSync(sdkDist)) {
    process.exit(0);
}

console.log("[kestra-sdk] No build found — bundling the committed SDK source once (subsequent runs are instant).");

try {
    execSync("npm run build:sdk", { stdio: "inherit" });
} catch {
    console.error("[kestra-sdk] Building the committed SDK source failed (see above).");
    process.exit(1);
}

if (!existsSync(sdkDist)) {
    console.error("[kestra-sdk] Build reported success but no dist output was produced.");
    process.exit(1);
}
