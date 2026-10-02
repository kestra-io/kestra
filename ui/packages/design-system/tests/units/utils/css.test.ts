import {afterEach, beforeEach, describe, expect, test} from "vitest"
import {cssVar} from "../../../src/utils/css"

const colorVariable = "--test-color"
const rawVariable = "--test-raw"

describe("cssVar", () => {
    beforeEach(() => {
        document.documentElement.style.setProperty(colorVariable, "#029E73")
        document.documentElement.style.setProperty(rawVariable, "  raw value  ")
    })

    afterEach(() => {
        document.documentElement.style.removeProperty(colorVariable)
        document.documentElement.style.removeProperty(rawVariable)
    })

    test("returns the trimmed custom-property value without opacity", () => {
        expect(cssVar(rawVariable)).toBe("raw value")
    })

    test("converts a six-digit hex value to rgba with the given opacity", () => {
        expect(cssVar(colorVariable, 0.5)).toBe("rgba(2, 158, 115, 0.5)")
    })

    test("returns an empty string for a missing custom property", () => {
        expect(cssVar("--test-missing")).toBe("")
    })

    test("honors an opacity of zero", () => {
        expect(cssVar(colorVariable, 0)).toBe("rgba(2, 158, 115, 0)")
    })
})
