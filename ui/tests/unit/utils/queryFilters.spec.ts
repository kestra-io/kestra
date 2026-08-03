import {describe, expect, it} from "vitest"
import {Comparators, encodeFilterGroupsToQuery, keyOfComparator} from "@kestra-io/design-system"
import {createConfigureClient} from "../../../packages/hey-api-plugin/src/runtime"
import {routeQueryToQueryFilters} from "../../../src/utils/queryFilters"

const serializeQueryFilters = (filters: ReturnType<typeof routeQueryToQueryFilters>) => {
    let config: any
    const slot = {clear() {}, use() {}}
    const client = {
        setConfig(value: any) { config = value },
        interceptors: {request: slot, response: slot, error: slot},
    }
    createConfigureClient(client, {bodySerializer() {}})()
    return config.querySerializer({filters}) as string
}

describe("routeQueryToQueryFilters", () => {
    it("builds a simple leaf filter", () => {
        expect(routeQueryToQueryFilters({"filters[namespace][EQUALS]": "io.kestra"})).toEqual([
            {field: "namespace", operation: "EQUALS", value: "io.kestra"},
        ])
    })

    it("keeps the irregular q/groupList field values as-is (already the wire form)", () => {
        expect(routeQueryToQueryFilters({"filters[q][EQUALS]": "hello"})).toEqual([
            {field: "q", operation: "EQUALS", value: "hello"},
        ])
        expect(routeQueryToQueryFilters({"filters[groupList][EQUALS]": "team"})).toEqual([
            {field: "groupList", operation: "EQUALS", value: "team"},
        ])
    })

    it("keeps a comma-joined IN/NOT_IN value as a plain string", () => {
        expect(routeQueryToQueryFilters({"filters[namespace][IN]": "a,b,c"})).toEqual([
            {field: "namespace", operation: "IN", value: "a,b,c"},
        ])
    })

    it("merges multiple label sub-keys sharing an operation into one filter", () => {
        expect(routeQueryToQueryFilters({
            "filters[labels][EQUALS][env]": "prod",
            "filters[labels][EQUALS][team]": "backend",
        })).toEqual([
            {field: "labels", operation: "EQUALS", value: {env: "prod", team: "backend"}},
        ])
    })

    it("builds an OR group for repeated IN values of the same label", () => {
        expect(routeQueryToQueryFilters({
            "filters[labels][IN][environment]": ["production", "staging"],
        })).toEqual([
            {
                logical: "or",
                children: [
                    {field: "labels", operation: "IN", value: {environment: "production"}},
                    {field: "labels", operation: "IN", value: {environment: "staging"}},
                ],
            },
        ])
    })

    it("builds an AND group for repeated NOT_IN values of the same label", () => {
        expect(routeQueryToQueryFilters({
            "filters[labels][NOT_IN][environment]": ["production", "staging"],
        })).toEqual([
            {
                logical: "and",
                children: [
                    {field: "labels", operation: "NOT_IN", value: {environment: "production"}},
                    {field: "labels", operation: "NOT_IN", value: {environment: "staging"}},
                ],
            },
        ])
    })

    it.each(["constructor", "toString", "__proto__"])(
        "preserves repeated values for the reserved label key %s",
        (labelKey) => {
            expect(routeQueryToQueryFilters({
                [`filters[labels][IN][${labelKey}]`]: ["production", "staging"],
            })).toEqual([
                {
                    logical: "or",
                    children: [
                        {field: "labels", operation: "IN", value: {[labelKey]: "production"}},
                        {field: "labels", operation: "IN", value: {[labelKey]: "staging"}},
                    ],
                },
            ])
        },
    )

    it.each([
        ["IN", "or"],
        ["NOT_IN", "and"],
    ] as const)("serializes repeated label %s values as grouped URL parameters", (operation, logical) => {
        const filters = routeQueryToQueryFilters({
            [`filters[labels][${operation}][environment]`]: ["production", "staging"],
        })
        const params = new URLSearchParams(serializeQueryFilters(filters))

        expect(params.getAll(`filters[${logical}][0][labels][${operation}][environment]`)).toEqual(["production"])
        expect(params.getAll(`filters[${logical}][1][labels][${operation}][environment]`)).toEqual(["staging"])
    })

    it.each([
        [Comparators.IN, "or"],
        [Comparators.NOT_IN, "and"],
    ] as const)("preserves colons through repeated label %s route and wire serialization", (comparator, logical) => {
        const query = encodeFilterGroupsToQuery([{
            id: "g1",
            kind: "leaf",
            filters: [{
                id: "f1",
                key: "labels",
                keyLabel: "Labels",
                comparator,
                comparatorLabel: comparator,
                value: ["url:https://prod:8443/a", "url:https://stage:9443/b"],
                valueLabel: "url:https://prod:8443/a, url:https://stage:9443/b",
            }],
        }], keyOfComparator)
        const params = new URLSearchParams(serializeQueryFilters(routeQueryToQueryFilters(query)))
        const operation = keyOfComparator(comparator)

        expect(params.get(`filters[${logical}][0][labels][${operation}][url]`)).toBe("https://prod:8443/a")
        expect(params.get(`filters[${logical}][1][labels][${operation}][url]`)).toBe("https://stage:9443/b")
    })

    it("serializes a leaf alongside a nested logical group", () => {
        const filters = routeQueryToQueryFilters({
            "filters[tenantId][EQUALS]": "tenant-1",
            "filters[or][0][and][0][namespace][EQUALS]": "io.kestra",
            "filters[or][0][and][1][state][EQUALS]": "RUNNING",
            "filters[or][1][state][EQUALS]": "FAILED",
        })
        const params = new URLSearchParams(serializeQueryFilters(filters))

        expect(params.get("filters[tenantId][EQUALS]")).toBe("tenant-1")
        expect(params.get("filters[or][0][and][0][namespace][EQUALS]")).toBe("io.kestra")
        expect(params.get("filters[or][0][and][1][state][EQUALS]")).toBe("RUNNING")
        expect(params.get("filters[or][1][state][EQUALS]")).toBe("FAILED")
    })

    it("builds a top-level OR group", () => {
        expect(routeQueryToQueryFilters({
            "filters[or][0][state][EQUALS]": "RUNNING",
            "filters[or][1][state][EQUALS]": "FAILED",
        })).toEqual([
            {
                logical: "or",
                children: [
                    {field: "state", operation: "EQUALS", value: "RUNNING"},
                    {field: "state", operation: "EQUALS", value: "FAILED"},
                ],
            },
        ])
    })

    it("flattens a single-branch AND group back to a plain leaf", () => {
        expect(routeQueryToQueryFilters({"filters[and][0][state][EQUALS]": "RUNNING"})).toEqual([
            {field: "state", operation: "EQUALS", value: "RUNNING"},
        ])
    })

    it("builds a nested wrapper group (OR containing an AND)", () => {
        expect(routeQueryToQueryFilters({
            "filters[or][0][and][0][namespace][EQUALS]": "io.kestra",
            "filters[or][0][and][1][state][EQUALS]": "RUNNING",
            "filters[or][1][state][EQUALS]": "FAILED",
        })).toEqual([
            {
                logical: "or",
                children: [
                    {
                        logical: "and",
                        children: [
                            {field: "namespace", operation: "EQUALS", value: "io.kestra"},
                            {field: "state", operation: "EQUALS", value: "RUNNING"},
                        ],
                    },
                    {field: "state", operation: "EQUALS", value: "FAILED"},
                ],
            },
        ])
    })

    it("builds the timeRange GTE/LTE pair as two independent leaves", () => {
        expect(routeQueryToQueryFilters({
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2023-01-01T00:00:00.000Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2023-01-31T23:59:59.000Z",
        })).toEqual([
            {field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: "2023-01-01T00:00:00.000Z"},
            {field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: "2023-01-31T23:59:59.000Z"},
        ])
    })

    it("ignores non-filters keys", () => {
        expect(routeQueryToQueryFilters({page: "1", size: "25", sort: "id:asc"})).toEqual([])
    })
})
