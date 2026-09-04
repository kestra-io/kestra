import {describe, expect, test} from "vitest"

import {
    Comparators,
    isLeafGroup,
    type FilterConfiguration,
} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import {parseEncodedGroups} from "../../../../src/components/Data/KsDataTable/filter/utils/routeDecoder"
import {DATE_FILTER_KEY} from "../../../../src/components/Data/KsDataTable/filter/utils/constants"

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

const timeRangeConfiguration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "timeRange",
            label: "Interval",
            valueType: "time-range",
            customDateMode: "range",
            comparators: [
                Comparators.GREATER_THAN_OR_EQUAL_TO,
                Comparators.LESS_THAN_OR_EQUAL_TO,
            ],
            dateFilterOptions: [
                {value: "START_DATE", label: "Start date"},
                {value: "END_DATE", label: "End date"},
                {value: "START_OR_END_DATE", label: "Start or end date"},
            ],
        },
    ],
}

describe("custom range decoding", () => {
    test("carries the route's dateFilter onto the merged range chip", () => {
        const query = {
            "filters[timeRange][GREATER_THAN_OR_EQUAL_TO]": "2026-08-01T00:00:00.000Z",
            "filters[timeRange][LESS_THAN_OR_EQUAL_TO]": "2026-08-02T00:00:00.000Z",
            [DATE_FILTER_KEY]: "END_DATE",
        }

        const {groups} = parseEncodedGroups(query, timeRangeConfiguration)
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        // The two bounds are merged into one chip by a pre-pass that used to drop the meta,
        // leaving the Apply-to selector on its first option instead of the one in the URL.
        expect(group.filters).toHaveLength(1)
        expect(group.filters[0]?.meta).toEqual({dateFilter: "END_DATE"})
    })

    test("leaves the merged range chip without meta when the route carries no dateFilter", () => {
        const query = {
            "filters[timeRange][GREATER_THAN_OR_EQUAL_TO]": "2026-08-01T00:00:00.000Z",
            "filters[timeRange][LESS_THAN_OR_EQUAL_TO]": "2026-08-02T00:00:00.000Z",
        }

        const {groups} = parseEncodedGroups(query, timeRangeConfiguration)
        const [group] = groups

        if (!group || !isLeafGroup(group)) {
            throw new Error("Expected the decoded group to be a leaf group")
        }

        expect(group.filters[0]?.meta).toBeUndefined()
    })
})
