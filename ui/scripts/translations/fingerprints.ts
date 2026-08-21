/**
 * Reading and writing the fingerprint sidecar.
 *
 * A fingerprint records the English text a translation was generated from, so "has this drifted?"
 * can be answered from the files alone. The generator writes these; the checkers read them.
 *
 * The rules themselves — how a key path is built, how a value is hashed — live in
 * `./fingerprintRules.mjs` so the dependency-free PR gate can apply them too. Both sides have to
 * agree on those exactly, and when they were implemented separately they disagreed almost
 * immediately: one flattened `a.b.c` while the other flattened `a|b|c`, so every lookup missed and
 * the check reported 1,249 perfectly good keys as stale. That failure is silent and points the
 * wrong way, since the remedy it suggests is to re-translate everything.
 */
import {existsSync, readFileSync} from "node:fs"
import {writeIfChanged} from "./files.ts"

export {KEY_SEPARATOR, fingerprintOf, flattenStrings, staleKeys} from "./fingerprintRules.mjs"

export type Fingerprints = {[key: string]: string};

export function readFingerprints(filePath: string | undefined): Fingerprints {
    if (!filePath || !existsSync(filePath)) return {}
    try {
        return JSON.parse(readFileSync(filePath, "utf-8")) as Fingerprints
    } catch (e) {
        console.warn(`Could not read fingerprints from "${filePath}", treating every key as stale: ${e}`)
        return {}
    }
}

/** Writes fingerprints in sorted key order, so the file diffs cleanly and never churns on reorder. */
export function writeFingerprints(filePath: string | undefined, fingerprints: Fingerprints): void {
    if (!filePath) return
    const sorted: Fingerprints = {}
    for (const key of Object.keys(fingerprints).sort()) {
        sorted[key] = fingerprints[key]
    }
    writeIfChanged(filePath, JSON.stringify(sorted, null, 2))
}
