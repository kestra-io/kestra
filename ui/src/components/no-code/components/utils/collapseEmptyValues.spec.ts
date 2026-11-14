import {describe, it, expect} from "vitest"
import {collapseEmptyValues} from "./collapseEmptyValues"

describe("collapseEmptyValues", () => {
    it.each([
        ["an empty string", ""],
        ["null", null],
        ["an empty object", {}],
    ])("collapses %s to undefined", (_, value) => {
        expect(collapseEmptyValues(value)).toBeUndefined()
    })

    it.each([
        ["0", 0],
        ["false", false],
        ["an empty array", []],
    ])("preserves %s", (_, value) => {
        expect(collapseEmptyValues(value)).toBe(value)
    })

    it("keeps undefined as undefined", () => {
        expect(collapseEmptyValues(undefined)).toBeUndefined()
    })

    it.each([
        ["a non-empty string", "hello"],
        ["a non-empty object", {key: "value"}],
        ["a non-empty array", [1, 2, 3]],
    ])("returns %s unchanged", (_, value) => {
        expect(collapseEmptyValues(value)).toBe(value)
    })

    // JSON.stringify drops undefined properties, so this serialises to "{}".
    it("collapses an object whose only value is undefined", () => {
        expect(collapseEmptyValues({value: undefined})).toBeUndefined()
    })

    it("returns a non-empty nested object unchanged", () => {
        const value = {settings: {enabled: false}}
        expect(collapseEmptyValues(value)).toBe(value)
    })
})
