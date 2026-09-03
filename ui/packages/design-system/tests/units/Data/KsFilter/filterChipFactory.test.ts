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
