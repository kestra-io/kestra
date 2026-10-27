import {describe, expect, test} from "vitest"
import type {LocationQuery} from "vue-router"
import {Comparators, type FilterConfiguration} from "./filterTypes"
import {parseEncodedGroups} from "./routeDecoder"

const configuration: FilterConfiguration = {
    title: "Filters",
    keys: [
        {
            key: "status",
            label: "Status",
            valueType: "select",
            comparators: [Comparators.EQUALS, Comparators.CONTAINS],
        },
        {
            key: "amount",
            label: "Amount",
            valueType: "number",
            comparators: [
                Comparators.BETWEEN,
                Comparators.GREATER_THAN_OR_EQUAL_TO,
                Comparators.LESS_THAN_OR_EQUAL_TO,
            ],
        },
    ],
}

const decode = (query: LocationQuery) => parseEncodedGroups(query, configuration)

describe("parseEncodedGroups", () => {
    test("returns one empty leaf for an empty query", () => {
        const {groups, topLogical} = decode({})

        expect(groups).toHaveLength(1)
        expect(groups[0]).toMatchObject({kind: "leaf", filters: []})
        expect(topLogical).toBe("OR")
    })

    test("puts one filter parameter in one leaf", () => {
        const {groups} = decode({"filters[status][EQUALS]": "RUNNING"})

        expect(groups).toHaveLength(1)
        expect(groups[0]).toMatchObject({
            kind: "leaf",
            filters: [
                expect.objectContaining({
                    key: "status",
                    comparator: Comparators.EQUALS,
                    value: "RUNNING",
                }),
            ],
        })
    })

    test("buckets parameters with group indexes into separate top-level groups", () => {
        const {groups, topLogical} = decode({
            "filters[and][0][status][EQUALS]": "RUNNING",
            "filters[and][1][status][EQUALS]": "FAILED",
        })

        expect(topLogical).toBe("AND")
        expect(groups).toHaveLength(2)
        expect(groups.map(group => group.kind === "wrapper" ? [] : group.filters[0]?.value))
            .toEqual(["RUNNING", "FAILED"])
    })

    test("nests wrapper child indexes inside a wrapper group", () => {
        const {groups} = decode({
            "filters[or][0][and][0][status][EQUALS]": "RUNNING",
            "filters[or][0][and][1][status][EQUALS]": "FAILED",
        })

        expect(groups).toHaveLength(1)
        expect(groups[0]).toMatchObject({
            kind: "wrapper",
            logical: "AND",
            children: [
                {kind: "leaf", filters: [expect.objectContaining({value: "RUNNING"})]},
                {kind: "leaf", filters: [expect.objectContaining({value: "FAILED"})]},
            ],
        })
    })

    test("keeps the wrapper logical operator from the query", () => {
        const {groups} = decode({
            "filters[or][0][or][0][status][EQUALS]": "RUNNING",
            "filters[or][0][or][1][status][EQUALS]": "FAILED",
        })

        expect(groups[0]).toMatchObject({kind: "wrapper", logical: "OR"})
    })

    test("preserves same-field filters with different comparators", () => {
        const {groups} = decode({
            "filters[status][EQUALS]": "RUNNING",
            "filters[status][CONTAINS]": "RUN",
        })

        const filters = groups[0]?.kind === "wrapper" ? [] : groups[0]?.filters ?? []
        expect(filters).toHaveLength(2)
        expect(filters.map(filter => [filter.comparator, filter.value]))
            .toEqual([
                [Comparators.EQUALS, "RUNNING"],
                [Comparators.CONTAINS, "RUN"],
            ])
    })

    test("merges greater-than and less-than bounds into one range filter", () => {
        const {groups} = decode({
            "filters[amount][GREATER_THAN_OR_EQUAL_TO]": "10",
            "filters[amount][LESS_THAN_OR_EQUAL_TO]": "20",
        })

        const filters = groups[0]?.kind === "wrapper" ? [] : groups[0]?.filters ?? []
        expect(filters).toHaveLength(1)
        expect(filters[0]).toMatchObject({
            key: "amount",
            comparator: Comparators.BETWEEN,
            value: ["10", "20"],
        })
    })

    test("uses the first value when a query parameter is repeated", () => {
        const {groups} = decode({"filters[status][EQUALS]": ["RUNNING", "FAILED"]})

        const filters = groups[0]?.kind === "wrapper" ? [] : groups[0]?.filters ?? []
        expect(filters[0]?.value).toBe("RUNNING")
    })

    test("ignores unparsable and unknown parameters", () => {
        const {groups} = decode({
            invalid: "value",
            "filters[status]": "RUNNING",
            "filters[unknown][EQUALS]": "value",
        })

        expect(groups).toHaveLength(1)
        expect(groups[0]).toMatchObject({kind: "leaf", filters: []})
    })
})
