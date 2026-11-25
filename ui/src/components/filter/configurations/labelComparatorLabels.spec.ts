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
        // The translator echoes the key it receives, so the result exposes the keys the source really uses.
        const usedKeys = Object.values(labelComparatorLabels(key => key))

        expect(usedKeys).toHaveLength(expectedMappings.length)
        expect(new Set(usedKeys).size).toBe(usedKeys.length)
    })

    it("uses the translator return value verbatim", () => {
        const translations = new Map<string, string>(expectedMappings.map(([, key], index) => [key, ` Label ${index} (<b>raw</b>) `]))
        const t = vi.fn((key: string) => translations.get(key) ?? "")

        const result = labelComparatorLabels(t)

        for (const [comparator, key] of expectedMappings) {
            expect(result[comparator]).toBe(translations.get(key))
        }
    })

    it("omits comparators without a label mapping", () => {
        const result = labelComparatorLabels(key => key)
        const mappedComparators: string[] = expectedMappings.map(([comparator]) => comparator)
        const unmappedComparators = Object.values(Comparators).filter(comparator => !mappedComparators.includes(comparator))

        expect(unmappedComparators).toContain(Comparators.NOT_EQUALS)

        for (const comparator of unmappedComparators) {
            // `in` instead of `toHaveProperty`, which would read the dots of "^.=" as a nested path.
            expect(comparator in result).toBe(false)
        }

        expect(Object.keys(result).sort()).toEqual([...mappedComparators].sort())
    })
})
