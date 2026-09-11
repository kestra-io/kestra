import {describe, expect, it, vi} from "vitest"
import {boundForDisplay, capForDisplay, DISPLAY_MAX_LINE_CHARS, flatten, PREVIEW_MAX_ENTRIES} from "./utils"

vi.mock("@kestra-io/design-system", () => ({
    fileUtils: {isFileUri: () => false},
    copyToClipboard: vi.fn(),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))

describe("flatten", () => {
    it("should key every leaf by its path, keeping nulls and empty containers", () => {
        expect(flatten({a: {b: 1}, c: null, d: {}, e: [1, 2]})).toEqual({
            "a.b": 1,
            "c": null,
            "d": {},
            "e.0": 1,
            "e.1": 2,
        })
    })

    it("should flatten more leaves than a spread can carry as arguments", () => {
        const wide = Object.fromEntries(Array.from({length: 150_000}, (_, index) => [`item_${index}`, index]))

        expect(Object.keys(flatten(wide))).toHaveLength(150_000)
    })
})

describe("capForDisplay", () => {
    it("should clip a single long line even when the value is under every other limit", () => {
        const text = ["short", "x".repeat(5000), "short"].join("\n")

        const capped = capForDisplay(text)

        expect(capped.split("\n").map((line) => line.length)).toEqual([5, DISPLAY_MAX_LINE_CHARS, 5])
    })
})

describe("boundForDisplay", () => {
    it("should stay parseable where clipping the serialized text does not", () => {
        const value = {tasks: Array.from({length: 15_000}, (_, index) => ({uid: `task ${index}`}))}

        const {value: bounded, truncated} = boundForDisplay(value)

        expect(truncated).toBe(true)
        expect(() => JSON.parse(JSON.stringify(bounded, null, 2))).not.toThrow()
        expect(() => JSON.parse(capForDisplay(JSON.stringify(value, null, 2)))).toThrow()
    })

    it("should name how many entries it dropped, per container kind", () => {
        const {value: bounded} = boundForDisplay({
            list: Array.from({length: 150}, (_, index) => index),
            record: Object.fromEntries(Array.from({length: 150}, (_, index) => [`k${index}`, index])),
        }) as {value: {list: unknown[]; record: Record<string, unknown>}}

        expect(bounded.list).toHaveLength(PREVIEW_MAX_ENTRIES + 1)
        expect(bounded.list.at(-1)).toBe(`… ${150 - PREVIEW_MAX_ENTRIES}`)
        expect(bounded.record["…"]).toBe(150 - PREVIEW_MAX_ENTRIES)
    })

    it("should return a small value untouched and unflagged", () => {
        const value = {a: {b: [1, null, "x"]}, c: {}}

        expect(boundForDisplay(value)).toEqual({value, truncated: false})
    })
})
