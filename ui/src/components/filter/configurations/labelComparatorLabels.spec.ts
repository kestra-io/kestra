import {describe, expect, it, vi} from "vitest"
import {Comparators} from "@kestra-io/design-system"

import {labelComparatorLabels} from "./labelComparatorLabels"

const expectedMappings = [
    [Comparators.IN, "filter.label_comparators.has_any_of"],
    [Comparators.NOT_IN, "filter.label_comparators.has_none_of"],
    [Comparators.EQUALS, "filter.label_comparators.has_all_of"],
    [Comparators.CONTAINS, "filter.label_comparators.contains"],
    [Comparators.NOT_CONTAINS, "filter.label_comparators.does_not_contain"],
    [Comparators.IS_NOT_NULL, "filter.label_comparators.is_set"],
    [Comparators.IS_NULL, "filter.label_comparators.is_not_set"],
] as const

describe("labelComparatorLabels", () => {
    it("maps each comparator to its own translation key", () => {
        const t = vi.fn((key: string) => `translated:${key}`)

        const result = labelComparatorLabels(t)

        for (const [comparator, key] of expectedMappings) {
            expect(t).toHaveBeenCalledWith(key)
            expect(result[comparator]).toBe(`translated:${key}`)
        }

        expect(t).toHaveBeenCalledTimes(expectedMappings.length)
    })

    it("uses unique translation keys for every comparator", () => {
        const keys = expectedMappings.map(([, key]) => key)

        expect(new Set(keys).size).toBe(keys.length)
    })

    it("uses the translator return value verbatim", () => {
        const translatedValue = "Custom translated label"
        const t = vi.fn(() => translatedValue)

        const result = labelComparatorLabels(t)

        for (const [comparator] of expectedMappings) {
            expect(result[comparator]).toBe(translatedValue)
        }
    })

    it("omits comparators without a label mapping", () => {
        const result = labelComparatorLabels(key => key)

        expect(result).not.toHaveProperty(Comparators.NOT_EQUALS)
    })
})
