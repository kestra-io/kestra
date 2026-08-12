import {describe, expect, it} from "vitest"
import {distinctSkipReasons} from "../../../src/utils/sourceSearchDiff"

describe("distinctSkipReasons", () => {
    it("deduplicates reasons and orders them by severity", () => {
        expect(distinctSkipReasons([
            {reason: "NO_CHANGE"},
            {reason: "READ_ONLY"},
            {reason: "NO_CHANGE"},
        ])).toEqual(["READ_ONLY", "NO_CHANGE"])
    })

    it("falls back to UNKNOWN for reasons it does not recognize", () => {
        expect(distinctSkipReasons([{reason: "SOMETHING_NEW"}])).toEqual(["UNKNOWN"])
        expect(distinctSkipReasons([{}])).toEqual(["UNKNOWN"])
    })
})
