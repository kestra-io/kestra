// The pure fingerprint rules: how a key path is built, and how a value is hashed.
//
// Plain JavaScript, like `translationRules.mjs` and for the same reason — the PR gate imports this
// and has to run before any `npm ci`. The convention across this folder is that *rules* live in
// `.mjs` so every consumer can reach them, while file IO and orchestration stay in `.ts`.
//
// `node:crypto` is a builtin, so importing it costs the gate nothing.

import {createHash} from "node:crypto"

/** Key separator. Chosen because `.` and `_` both occur inside real translation keys. */
export const KEY_SEPARATOR = "|"

/**
 * Flattens a nested translation object into `a|b|c` keys holding only string leaves.
 *
 * Arrays are descended into by index, so `thinkingWords: ["a", "b"]` becomes `thinkingWords|0` and
 * `thinkingWords|1` — the generator translates those elements individually and rebuilds the array,
 * so they need fingerprints of their own.
 */
export function flattenStrings(value, prefix = "") {
    if (typeof value === "string") {
        return {[prefix]: value}
    }
    if (value === null || typeof value !== "object") {
        return {}
    }
    return Object.entries(value).reduce((out, [key, child]) => {
        return Object.assign(out, flattenStrings(child, prefix ? `${prefix}${KEY_SEPARATOR}${key}` : key))
    }, {})
}

/** Short content hash of an English source string. Only ever compared for equality. */
export function fingerprintOf(englishValue) {
    return createHash("sha256").update(englishValue).digest("hex").slice(0, 12)
}

/** Keys whose English text no longer matches what their translations were generated from. */
export function staleKeys(englishRoot, fingerprints, keyPrefix = "") {
    return Object.entries(flattenStrings(englishRoot))
        .filter(([key, message]) => fingerprints[`${keyPrefix}${key}`] !== fingerprintOf(message))
        .map(([key]) => key)
}
