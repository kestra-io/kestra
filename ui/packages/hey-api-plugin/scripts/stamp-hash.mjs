#!/usr/bin/env node
// Stamp (or verify) the OpenAPI spec hash at the bottom of a generated SDK index file.
//
// The committed SDK carries the hash of the openapi.yml it was generated from. A CI
// drift-check can then tell — cheaply, without re-running the slow Gradle-bound SDK
// generation — whether the checked-in SDK still matches the backend's current spec:
// it regenerates only openapi.yml, hashes it, and compares to the stamped value.
//
// Usage:
//   hey-api-stamp-hash <spec.yml> <index.ts>           # stamp the hash into <index.ts>
//   hey-api-stamp-hash <spec.yml> <index.ts> --check   # exit 0 if in sync, 1 if drifted
//
// The hash is sha256(spec) truncated to 16 hex chars — trivially reproducible in bash
// too (`sha256sum spec | cut -c1-16`), so the workflow does not have to depend on this bin.
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";

const [specPath, indexPath, mode] = process.argv.slice(2);

if (!specPath || !indexPath) {
    console.error("usage: hey-api-stamp-hash <spec.yml> <index.ts> [--check]");
    process.exit(2);
}

const MARKER = "// openapi-hash:";
const hash = createHash("sha256").update(readFileSync(specPath)).digest("hex").slice(0, 16);

if (mode === "--check") {
    const committed = readFileSync(indexPath, "utf8").match(/\/\/ openapi-hash: ([a-f0-9]+)/)?.[1];
    if (committed === hash) {
        console.log(`[hey-api-stamp-hash] SDK is up to date (${hash})`);
        process.exit(0);
    }
    console.error(`[hey-api-stamp-hash] SDK is out of date — committed=${committed ?? "none"} spec=${hash}`);
    process.exit(1);
}

// Strip any previous stamp, then append a fresh one at the very bottom.
let src = readFileSync(indexPath, "utf8").replace(/\n*\/\/ openapi-hash:[\s\S]*$/, "").trimEnd();
src += `\n\n${MARKER} ${hash}\nexport const OPENAPI_SPEC_HASH = "${hash}";\n`;
writeFileSync(indexPath, src);
console.log(`[hey-api-stamp-hash] stamped openapi-hash ${hash} into ${indexPath}`);
