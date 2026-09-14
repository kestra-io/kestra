import {describe, expect, test} from "vitest"

import {
    Comparators,
    isLeafGroup,
    type FilterConfiguration,
} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import {parseEncodedGroups} from "../../../../src/components/Data/KsDataTable/filter/utils/routeDecoder"

const labelsConfiguration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "labels",
            label: "Labels",
            valueType: "key-value",
            comparators: [
                Comparators.IN,
                Comparators.NOT_IN,
                Comparators.EQUALS,
                Comparators.NOT_EQUALS,
                Comparators.CONTAINS,
                Comparators.NOT_CONTAINS,
                Comparators.IS_NOT_NULL,
                Comparators.IS_NULL,
            ],
        },
    ],
}

describe("filterChipFactory", () => {
    test.each([
        [Comparators.CONTAINS, "CONTAINS"],
        [Comparators.NOT_CONTAINS, "NOT_CONTAINS"],
    ])(
        "restores labels %s text values as strings",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}][team]`]: "core",
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: "team:core",
                valueLabel: "team:core",
            })
        },
    )

    test.each([
        [Comparators.IN, "IN"],
        [Comparators.NOT_IN, "NOT_IN"],
    ])(
        "restores labels %s key-value values as arrays",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}][team]`]: "core",
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: ["team:core"],
                valueLabel: "team:core",
            })
        },
    )

    test.each([
        [Comparators.IN, "IN"],
        [Comparators.NOT_IN, "NOT_IN"],
    ])(
        "restores repeated labels %s values after a URL reload",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}][environment]`]: ["production", "staging"],
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: ["environment:production", "environment:staging"],
                valueLabel: "environment:production +1",
            })
        },
    )

    test.each([
        [Comparators.IS_NOT_NULL, "IS_NOT_NULL"],
        [Comparators.IS_NULL, "IS_NULL"],
    ])(
        "restores labels %s key-only values as strings",
        (comparator, operation) => {
            const query = {
                [`filters[labels][${operation}]`]: "team",
            }

            const {groups} = parseEncodedGroups(query, labelsConfiguration)
            const [group] = groups

            if (!group || !isLeafGroup(group)) {
                throw new Error("Expected the decoded group to be a leaf group")
            }

            expect(group.filters[0]).toMatchObject({
                key: "labels",
                comparator,
                value: "team",
                valueLabel: "team",
            })
        },
    )
})

const relativeRangeConfiguration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "timeRange",
            label: "Interval",
            valueType: "time-range",
            comparators: [Comparators.EQUALS],
        },
    ],
}

describe("relative date decoding", () => {
    test.each([
        ["P30D", "PT720H"],
        ["P1W", "PT168H"],
        ["P7D", "PT168H"],
        ["PT720H", "PT720H"],
    ])("normalizes the %s duration to %s", (value, expected) => {
        const {groups} = parseEncodedGroups({"filters[timeRange][EQUALS]": value}, relativeRangeConfiguration)
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        expect(group.filters[0]).toMatchObject({key: "timeRange", value: expected})
    })

    test("still reads an absolute date as a date rather than a duration", () => {
        const {groups} = parseEncodedGroups(
            {"filters[timeRange][EQUALS]": "2026-08-01T00:00:00.000Z"},
            relativeRangeConfiguration,
        )
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        expect(group.filters[0]?.value).toBeInstanceOf(Date)
    })
})
