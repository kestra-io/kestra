import {describe, expect, test} from "vitest"

import {Comparators, isLeafGroup, type FilterConfiguration} from "./filterTypes"
import {encodeFilterGroupsToQuery, isValidFilter, keyOfComparator} from "./helpers"
import {createNumberRangeFilter} from "./filterChipFactory"
import {parseEncodedGroups} from "./routeDecoder"

const configuration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "attempts",
            label: "Attempts",
            valueType: "number",
            comparators: [
                Comparators.GREATER_THAN_OR_EQUAL_TO,
                Comparators.GREATER_THAN,
                Comparators.LESS_THAN_OR_EQUAL_TO,
                Comparators.LESS_THAN,
                Comparators.BETWEEN,
            ],
        },
    ],
}

const [attemptsKey] = configuration.keys

describe("number between", () => {
    test("shouldEncodeBetweenAsInclusiveBoundsWhenWritingTheQuery", () => {
        const filter = createNumberRangeFilter("attempts", attemptsKey, "1", "5")

        const query = encodeFilterGroupsToQuery([{id: "g", filters: [filter]}], keyOfComparator)

        expect(query).toEqual({
            "filters[attempts][GREATER_THAN_OR_EQUAL_TO]": "1",
            "filters[attempts][LESS_THAN_OR_EQUAL_TO]": "5",
        })
    })

    test("shouldDecodeBothBoundsIntoOneBetweenChipWhenReadingTheQuery", () => {
        const {groups} = parseEncodedGroups({
            "filters[attempts][GREATER_THAN_OR_EQUAL_TO]": "1",
            "filters[attempts][LESS_THAN_OR_EQUAL_TO]": "5",
        }, configuration)

        const [group] = groups
        if (!group || !isLeafGroup(group)) throw new Error("Expected the decoded group to be a leaf group")
        expect(group.filters).toHaveLength(1)
        expect(group.filters[0]).toMatchObject({
            key: "attempts",
            comparator: Comparators.BETWEEN,
            value: ["1", "5"],
            valueLabel: "1 - 5",
        })
    })

    test("shouldKeepDecimalBoundsWhenRoundTrippingThroughTheQuery", () => {
        const query = encodeFilterGroupsToQuery(
            [{id: "g", filters: [createNumberRangeFilter("attempts", attemptsKey, "0.5", "2.75")]}],
            keyOfComparator,
        )

        const [group] = parseEncodedGroups(query, configuration).groups
        if (!group || !isLeafGroup(group)) throw new Error("Expected the decoded group to be a leaf group")
        expect(group.filters[0]).toMatchObject({value: ["0.5", "2.75"], valueLabel: "0.5 - 2.75"})
    })

    test("shouldKeepASingleBoundAsItsOwnComparatorWhenOnlyOneIsInTheQuery", () => {
        const {groups} = parseEncodedGroups({"filters[attempts][GREATER_THAN]": "3"}, configuration)

        const [group] = groups
        if (!group || !isLeafGroup(group)) throw new Error("Expected the decoded group to be a leaf group")
        expect(group.filters[0]).toMatchObject({comparator: Comparators.GREATER_THAN, value: "3"})
    })

    test("shouldKeepTheFirstValueWhenABoundIsRepeatedInTheQuery", () => {
        const {groups} = parseEncodedGroups({
            "filters[attempts][GREATER_THAN_OR_EQUAL_TO]": ["1", "2"],
            "filters[attempts][LESS_THAN_OR_EQUAL_TO]": "5",
        }, configuration)

        const [group] = groups
        if (!group || !isLeafGroup(group)) throw new Error("Expected the decoded group to be a leaf group")
        expect(group.filters[0]).toMatchObject({value: ["1", "5"]})
    })

    test("shouldKeepTheTighterBoundWhenABetweenAndAnotherBoundShareAGroup", () => {
        const between = createNumberRangeFilter("attempts", attemptsKey, "1", "10")
        const atLeast = {...between, id: "gte", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: "4"}

        const query = encodeFilterGroupsToQuery([{id: "g", filters: [atLeast, between]}], keyOfComparator)

        expect(query).toEqual({
            "filters[attempts][GREATER_THAN_OR_EQUAL_TO]": "4",
            "filters[attempts][LESS_THAN_OR_EQUAL_TO]": "10",
        })
    })

    test("shouldRejectBetweenWhenABoundIsMissingOrTheBoundsAreInverted", () => {
        expect(isValidFilter({key: "attempts", comparator: Comparators.BETWEEN, value: ["1", ""]})).toBe(false)
        expect(isValidFilter({key: "attempts", comparator: Comparators.BETWEEN, value: ["1", "5"]})).toBe(true)
        expect(isValidFilter({key: "attempts", comparator: Comparators.BETWEEN, value: ["10", "5"]})).toBe(false)
    })
})
