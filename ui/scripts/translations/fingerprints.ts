/**
 * The fingerprint contract, in one place.
 *
 * A fingerprint records the English text a translation was generated from, so "has this drifted?"
 * can be answered from the files alone. The generator writes these; the checker reads them.
 *
 * Both sides have to agree on *exactly* two things — how a nested translation object is flattened
 * into keys, and how a value is hashed — and neither agreement is enforced by the type system. When
 * they were implemented separately they disagreed almost immediately: the checker flattened
 * `a.b.c` while the generator flattened `a|b|c`, so every lookup missed and the check reported
 * 1,249 perfectly good keys as stale. That failure is silent and points the wrong way (it tells you
 * to re-translate everything), which is why the contract lives here rather than in either consumer.
 */
import {createHash} from "node:crypto"
import {existsSync, readFileSync} from "node:fs"
import {writeIfChanged} from "./files.ts"

/** Key separator. Chosen because `.` and `_` both occur inside real translation keys. */
export const KEY_SEPARATOR = "|"

export type Fingerprints = {[key: string]: string};

/**
 * Flattens a nested translation object into `a|b|c` keys holding only string leaves.
 *
 * Arrays are descended into by index, so `thinkingWords: ["a", "b"]` becomes `thinkingWords|0` and
 * `thinkingWords|1` — the generator translates those elements individually and rebuilds the array,
 * so they need fingerprints of their own.
 */
export function flattenStrings(value: unknown, prefix = ""): {[key: string]: string} {
    if (typeof value === "string") {
        return {[prefix]: value}
    }
    if (value === null || typeof value !== "object") {
        return {}
    }
    return Object.entries(value).reduce((out: {[key: string]: string}, [key, child]) => {
        return Object.assign(out, flattenStrings(child, prefix ? `${prefix}${KEY_SEPARATOR}${key}` : key))
    }, {})
}

/** Short content hash of an English source string. Only ever compared for equality. */
export function fingerprintOf(englishValue: string): string {
    return createHash("sha256").update(englishValue).digest("hex").slice(0, 12)
}

/** Keys whose English text no longer matches what their translations were generated from. */
export function staleKeys(englishRoot: unknown, fingerprints: Fingerprints): string[] {
    return Object.entries(flattenStrings(englishRoot))
        .filter(([key, message]) => fingerprints[key] !== fingerprintOf(message))
        .map(([key]) => key)
}

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
