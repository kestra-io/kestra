import {describe, expect, it} from "vitest"
import {firstReadingLine, parseOverride} from "./replayOverrides"

describe("parseOverride", () => {
    it("keeps the current value when the text was not edited", () => {
        expect(parseOverride(undefined, "")).toEqual({value: null})
    })

    it("keeps a string exactly as typed, spaces included", () => {
        expect(parseOverride("KR-0412 ", " KR-0412")).toEqual({value: " KR-0412"})
    })

    it.each([
        [-12, "5", {value: 5}],
        [-12, "five", {error: "number"}],
        [true, "false", {value: false}],
        [true, "yes", {error: "boolean"}],
        [{a: 1}, "{\"a\": 2}", {value: {a: 2}}],
        [{a: 1}, "{a: 2}", {error: "json"}],
    ])("follows the type of the current value: %j with %s", (original, text, expected) => {
        expect(parseOverride(original, text)).toEqual(expected)
    })
})

describe("firstReadingLine", () => {
    it("finds the first line reading one of the paths, 1-based", () => {
        expect(firstReadingLine("id: notify\nmessage: \"{{ outputs.fetch.values }}\"", ["outputs.fetch.values"])).toBe(2)
        expect(firstReadingLine("id: notify", ["outputs.fetch.values"])).toBeUndefined()
        expect(firstReadingLine("a: \"{{ outputs.fetch.values_other }}\"\nb: \"{{ outputs.fetch.values }}\"", ["outputs.fetch.values"])).toBe(2)
    })
})
