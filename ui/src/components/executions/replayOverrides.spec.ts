import {describe, expect, it} from "vitest"
import {parseOverride, type ReplayValue} from "./replayOverrides"

const rendered = (value: unknown): ReplayValue => ({expression: "outputs.fetch.value", kind: "EXPRESSION", value: value as ReplayValue["value"]})

describe("parseOverride", () => {
    it("keeps what the expression rendered when the text was not edited", () => {
        expect(parseOverride(rendered(undefined), "")).toEqual({value: null})
    })

    it("keeps a string exactly as typed, spaces included", () => {
        expect(parseOverride(rendered("KR-0412 "), " KR-0412")).toEqual({value: " KR-0412"})
    })

    it.each([
        [rendered(-12), "5", {value: 5}],
        [rendered(-12), "five", {error: "number"}],
        [rendered(true), "false", {value: false}],
        [rendered({a: 1}), "{\"a\": 2}", {value: {a: 2}}],
        [rendered({a: 1}), "{a: 2}", {error: "json"}],
    ])("follows the type of what the expression rendered: %j with %s", (value, text, expected) => {
        expect(parseOverride(value, text)).toEqual(expected)
    })
})
