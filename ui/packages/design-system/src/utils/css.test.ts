import {afterEach, beforeEach, describe, expect, test} from "vitest"
import {cssVar} from "./css"

const colorVariable = "--test-color"

describe("cssVar", () => {
    beforeEach(() => {
        document.documentElement.style.setProperty(colorVariable, "#029E73")
    })

    afterEach(() => {
        document.documentElement.style.removeProperty(colorVariable)
    })

    test("returns the custom-property value when no opacity is given", () => {
        expect(cssVar(colorVariable)).toBe("#029E73")
    })

    test("converts the hex value to rgba with the given opacity, zero included", () => {
        expect(cssVar(colorVariable, 0.5)).toBe("rgba(2, 158, 115, 0.5)")
        expect(cssVar(colorVariable, 0)).toBe("rgba(2, 158, 115, 0)")
    })
})
