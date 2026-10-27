import {describe, expect, it} from "vitest"
import {parseOverride, type ReplayValue} from "./replayOverrides"

const output = (value: unknown): ReplayValue => ({key: "outputs.fetch.value", kind: "OUTPUT", value: value as ReplayValue["value"]})
const dateInput = (value: unknown): ReplayValue => ({key: "inputs.day", kind: "INPUT", inputType: "DATE", value: value as ReplayValue["value"]})

describe("parseOverride", () => {
    it("keeps the original value when the text was not edited, even if it would not parse", () => {
        expect(parseOverride(dateInput(undefined), "")).toEqual({value: null})
    })

    it("keeps a string exactly as typed, spaces included", () => {
        expect(parseOverride(output("KR-0412 "), " KR-0412")).toEqual({value: " KR-0412"})
    })

    it.each([
        [output(-12), "5", {value: 5}],
        [output(-12), "five", {error: "number"}],
        [output(true), "false", {value: false}],
        [output({a: 1}), "{\"a\": 2}", {value: {a: 2}}],
        [output({a: 1}), "{a: 2}", {error: "json"}],
        [dateInput("2026-10-12"), "12/10/2026", {error: "date"}],
    ])("follows the type of the original value or input: %j with %s", (value, text, expected) => {
        expect(parseOverride(value, text)).toEqual(expected)
    })
})
