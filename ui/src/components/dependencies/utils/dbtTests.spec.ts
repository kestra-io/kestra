import {describe, expect, it} from "vitest"
import {hasDbtTests, normalizeDbtTests} from "./dbtTests"

describe("normalizeDbtTests", () => {
    it("keeps a complete roll-up", () => {
        expect(normalizeDbtTests("fail", 22, 2)).toEqual({state: "fail", total: 22, failed: 2})
    })

    it("treats a roll-up without a failed count as no tests, so a fail never reads 0/n", () => {
        expect(normalizeDbtTests("fail", 22, undefined)).toBeUndefined()
    })

    it("treats a zero or missing total as no tests", () => {
        expect(normalizeDbtTests("pass", 0, 0)).toBeUndefined()
        expect(normalizeDbtTests("pass", undefined, 0)).toBeUndefined()
    })

    it("ignores a status it does not recognise", () => {
        expect(normalizeDbtTests("skipped", 22, 0)).toBeUndefined()
    })
})

describe("hasDbtTests", () => {
    it("reads the three metadata keys together", () => {
        expect(hasDbtTests({dbtTestStatus: "pass", dbtTestsTotal: 3, dbtTestsFailed: 0})).toBe(true)
        expect(hasDbtTests({})).toBe(false)
    })
})
