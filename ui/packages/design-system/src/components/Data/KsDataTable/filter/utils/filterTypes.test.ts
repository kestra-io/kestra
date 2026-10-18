import {describe, test, expect} from "vitest"
import {
    Comparators,
    COMPARATOR_DESCRIPTIONS,
    COMPARATOR_LABELS,
    flipLogical,
    KV_COMPARATORS,
    NULL_COMPARATORS,
    RANGE_COMPARATORS,
    TEXT_COMPARATORS,
} from "./filterTypes"

describe("flipLogical", () => {
    test.each([
        ["AND", "OR"],
        ["OR", "AND"],
    ] as const)("changes %s to %s", (input, expected) => {
        expect(flipLogical(input)).toBe(expected)
    })

    test.each(["AND", "OR"] as const)("returns the original value when applied twice to %s", (operator) => {
        expect(flipLogical(flipLogical(operator))).toBe(operator)
    })
})

describe("comparator tables", () => {
    const comparatorTables = [
        ["TEXT_COMPARATORS", TEXT_COMPARATORS],
        ["RANGE_COMPARATORS", RANGE_COMPARATORS],
        ["KV_COMPARATORS", KV_COMPARATORS],
        ["NULL_COMPARATORS", NULL_COMPARATORS],
    ] as const

    test.each(comparatorTables)(
        "every comparator in %s has an entry in COMPARATOR_LABELS",
        (_, comparators) => {
            for (const comparator of comparators) {
                expect(COMPARATOR_LABELS[comparator]).toBeDefined()
                expect(typeof COMPARATOR_LABELS[comparator]).toBe("string")
                expect(COMPARATOR_LABELS[comparator].trim().length).toBeGreaterThan(0)
            }
        },
    )

    test("every comparator with a label has an entry in COMPARATOR_DESCRIPTIONS", () => {
        for (const comparator of Object.keys(COMPARATOR_LABELS) as Comparators[]) {
            expect(COMPARATOR_DESCRIPTIONS[comparator]).toBeDefined()
            expect(typeof COMPARATOR_DESCRIPTIONS[comparator]).toBe("string")
            expect(COMPARATOR_DESCRIPTIONS[comparator].trim().length).toBeGreaterThan(0)
        }
    })

    test.each(comparatorTables)(
        "no comparator table contains duplicate entries in %s",
        (_, comparators) => {
            const seen = new Set<Comparators>()
            for (const comparator of comparators) {
                expect(seen.has(comparator)).toBe(false)
                seen.add(comparator)
            }
            expect(seen.size).toBe(comparators.length)
        },
    )

    test("the four comparator sets do not overlap where they are intended to be distinct", () => {
        for (let i = 0; i < comparatorTables.length; i++) {
            for (let j = i + 1; j < comparatorTables.length; j++) {
                const [firstName, firstList] = comparatorTables[i]
                const [secondName, secondList] = comparatorTables[j]
                const secondSet = new Set(secondList)
                const intersection = firstList.filter((comparator) => secondSet.has(comparator))
                expect(
                    intersection,
                    `${firstName} and ${secondName} should not overlap`,
                ).toEqual([])
            }
        }
    })
})
