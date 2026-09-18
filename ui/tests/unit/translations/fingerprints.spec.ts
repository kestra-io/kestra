import {describe, expect, it} from "vitest"
import {
    fingerprintOf,
    flattenStrings,
    KEY_SEPARATOR,
    staleKeys,
} from "../../../scripts/translations/fingerprints.ts"

// These guard the contract between the generator and the checker. It has already broken once, in
// exactly the way a test would have caught: the two sides flattened keys differently, so every
// fingerprint lookup missed and 1,249 healthy keys were reported stale.
describe("flattenStrings", () => {
    it("joins nested keys with the shared separator", () => {
        expect(flattenStrings({a: {b: {c: "hello"}}})).toEqual({[`a${KEY_SEPARATOR}b${KEY_SEPARATOR}c`]: "hello"})
    })

    it("uses a separator that does not occur in real translation keys", () => {
        // `.` and `_` both appear inside keys such as `outputs-in-internal-storage` and
        // `settings|blocks|theme|fields|editor_folding_stratgy`, so neither can separate them.
        expect(KEY_SEPARATOR).toBe("|")
    })

    it("descends into arrays by index, so each element gets its own key", () => {
        // `ai.copilot.thinkingWords` is a real array in en.json; the generator translates its
        // elements individually and rebuilds the array, so they need fingerprints of their own.
        expect(flattenStrings({thinkingWords: ["Orchestrating", "Planning"]})).toEqual({
            "thinkingWords|0": "Orchestrating",
            "thinkingWords|1": "Planning",
        })
    })

    it("keeps only string leaves", () => {
        expect(flattenStrings({text: "yes", count: 3, on: true, nothing: null})).toEqual({text: "yes"})
    })

    it("returns nothing for an empty object", () => {
        expect(flattenStrings({})).toEqual({})
    })
})

describe("fingerprintOf", () => {
    it("is stable for the same text", () => {
        expect(fingerprintOf("Superadmin")).toBe(fingerprintOf("Superadmin"))
    })

    it("changes when the text changes, including in case only", () => {
        // The rename that started all this was a case change: "SuperAdmin" -> "Superadmin".
        expect(fingerprintOf("SuperAdmin")).not.toBe(fingerprintOf("Superadmin"))
    })

    it("is short enough to keep the sidecar readable", () => {
        expect(fingerprintOf("anything")).toHaveLength(12)
    })
})

describe("staleKeys", () => {
    const english = {superadmin: "Superadmin", cancel: "Cancel"}

    it("reports nothing when every fingerprint matches", () => {
        const fingerprints = {
            superadmin: fingerprintOf("Superadmin"),
            cancel: fingerprintOf("Cancel"),
        }
        expect(staleKeys(english, fingerprints)).toEqual([])
    })

    it("reports a key whose English source was edited after translating", () => {
        const fingerprints = {
            superadmin: fingerprintOf("SuperAdmin"), // the pre-rename text
            cancel: fingerprintOf("Cancel"),
        }
        expect(staleKeys(english, fingerprints)).toEqual(["superadmin"])
    })

    it("reports a key that has no fingerprint at all", () => {
        expect(staleKeys(english, {cancel: fingerprintOf("Cancel")})).toEqual(["superadmin"])
    })

    it("treats an empty sidecar as everything being stale", () => {
        expect(staleKeys(english, {})).toEqual(["superadmin", "cancel"])
    })

    it("ignores keys that exist only in the fingerprints", () => {
        const fingerprints = {
            superadmin: fingerprintOf("Superadmin"),
            cancel: fingerprintOf("Cancel"),
            removed: fingerprintOf("Gone from en.json"),
        }
        expect(staleKeys(english, fingerprints)).toEqual([])
    })
})
