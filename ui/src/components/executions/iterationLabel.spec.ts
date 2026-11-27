import {describe, expect, it, vi} from "vitest"
import type {ComposerTranslation} from "vue-i18n"
import {DISPLAY_MAX_LINE_CHARS} from "../../utils/utils"
import {iterationLabel} from "./iterationLabel"

function translation() {
    return vi.fn((key: string, named: {number: number}) => `${key}:${named.number}`)
}

describe("iterationLabel", () => {
    it("shouldReturnTheRunValueWithoutTranslation", () => {
        const t = translation()

        expect(iterationLabel(t as unknown as ComposerTranslation, {value: "item"}, 3)).toBe("item")
        expect(t).not.toHaveBeenCalled()
    })

    it.each([
        ["missing", {}],
        ["undefined", {value: undefined}],
        ["null", {value: null}],
        ["empty", {value: ""}],
    ])("shouldUseTheNumericPositionWhenTheValueIs%s", (_, run) => {
        const t = translation()

        expect(iterationLabel(t as unknown as ComposerTranslation, run, 3)).toBe("iteration_number:3")
        expect(t).toHaveBeenCalledWith("iteration_number", {number: 3})
    })

    it("shouldStringifyANonStringValue", () => {
        expect(iterationLabel(translation() as unknown as ComposerTranslation, {value: 42}, 3)).toBe("42")
    })

    it("shouldCapTheDisplayedValue", () => {
        const value = "x".repeat(DISPLAY_MAX_LINE_CHARS + 1)

        expect(iterationLabel(translation() as unknown as ComposerTranslation, {value}, 3)).toHaveLength(DISPLAY_MAX_LINE_CHARS)
    })
})
