import {describe, expect, it} from "vitest"
import {Comparators, encodeFilterGroupsToQuery, keyOfComparator} from "@kestra-io/design-system"
import type {QueryFilter} from "@kestra-io/kestra-sdk"
import {createConfigureClient} from "../../../packages/hey-api-plugin/src/runtime"
import {routeQueryToQueryFilters} from "../../../src/utils/queryFilters"

const serializeQuery = (query: Record<string, any>) => {
    let config: any
    const slot = {clear() {}, use() {}}
    const client = {
        setConfig(value: any) { config = value },
        interceptors: {request: slot, response: slot, error: slot},
    }
    createConfigureClient(client, {bodySerializer() {}})()
    return config.querySerializer(query) as string
}

const serializeQueryFilters = (filters: QueryFilter[]) => serializeQuery({filters})

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
        ["a single empty value", [""]],
        ["repeated empty values", ["", ""]],
        ["a null value", [null]],
    ])("drops label filters containing only %s", (_, value) => {
        expect(routeQueryToQueryFilters({
            "filters[labels][IN][environment]": value,
        })).toEqual([])
    })

    it("keeps valid label values while dropping empty route values", () => {
        expect(routeQueryToQueryFilters({
            "filters[labels][IN][environment]": [null, "", "production", ""],
        })).toEqual([
            {field: "labels", operation: "IN", value: {environment: "production"}},
        ])
    })

    it.each([
        ["CONTAINS", "team"],
        ["NOT_CONTAINS", "legacy"],
        ["IS_NULL", "deprecated"],
        ["IS_NOT_NULL", "owner"],
    ] as const)("preserves a scalar labels %s filter through the HTTP wire", (operation, value) => {
        const filters = routeQueryToQueryFilters({[`filters[labels][${operation}]`]: value})
        const params = new URLSearchParams(serializeQueryFilters(filters))

        expect(filters).toEqual([{field: "labels", operation, value}])
        expect(params.get(`filters[labels][${operation}]`)).toBe(value)
    })

    it("preserves scalar label filters inside mixed nested groups", () => {
        const filters = routeQueryToQueryFilters({
            "filters[tenantId][EQUALS]": "tenant-1",
            "filters[or][0][labels][CONTAINS]": "team",
            "filters[or][1][labels][IS_NULL]": "deprecated",
        })
        const params = new URLSearchParams(serializeQueryFilters(filters))

        expect(params.get("filters[tenantId][EQUALS]")).toBe("tenant-1")
        expect(params.get("filters[or][0][labels][CONTAINS]")).toBe("team")
        expect(params.get("filters[or][1][labels][IS_NULL]")).toBe("deprecated")
    })

    it("drops a label filter with an empty sub-key", () => {
        expect(routeQueryToQueryFilters({"filters[labels][IN][]": "production"})).toEqual([])
    })

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

    it.each(["IS_NULL", "IS_NOT_NULL"] as const)(
        "serializes a comparator-only %s leaf with an empty wire value",
        (operation) => {
            const params = new URLSearchParams(serializeQueryFilters([{field: "state", operation}]))

            expect(params.has(`filters[state][${operation}]`)).toBe(true)
            expect(params.get(`filters[state][${operation}]`)).toBe("")
        },
    )

    it("serializes comparator-only leaves before and after valued leaves", () => {
        const filters: QueryFilter[] = [
            {field: "state", operation: "IS_NULL"},
            {field: "namespace", operation: "EQUALS", value: "io.kestra"},
            {field: "status", operation: "IS_NOT_NULL", value: null},
        ]
        const params = new URLSearchParams(serializeQueryFilters(filters))

        expect(params.get("filters[state][IS_NULL]")).toBe("")
        expect(params.get("filters[namespace][EQUALS]")).toBe("io.kestra")
        expect(params.get("filters[status][IS_NOT_NULL]")).toBe("")
    })

    it("serializes comparator-only leaves inside logical groups", () => {
        const filters: QueryFilter[] = [{
            logical: "or",
            children: [
                {field: "state", operation: "IS_NULL"},
                {field: "status", operation: "IS_NOT_NULL", value: null},
            ],
        }]
        const params = new URLSearchParams(serializeQueryFilters(filters))

        expect(params.get("filters[or][0][state][IS_NULL]")).toBe("")
        expect(params.get("filters[or][1][status][IS_NOT_NULL]")).toBe("")
    })

    it("falls back atomically when a comparator-only leaf has an empty object value", () => {
        const filters = [
            {field: "labels", operation: "IS_NULL", value: {}},
            {field: "namespace", operation: "EQUALS", value: "io.kestra"},
        ]
        const query = serializeQuery({filters})

        expect(new URLSearchParams(query).getAll("filters")).toEqual(filters.map(value => JSON.stringify(value)))
    })

    it("falls back atomically when a logical group contains a comparator-only leaf with an empty object value", () => {
        const filters = [{
            logical: "or",
            children: [
                {field: "labels", operation: "IS_NULL", value: {}},
                {field: "namespace", operation: "EQUALS", value: "io.kestra"},
            ],
        }]
        const query = serializeQuery({filters})

        expect(new URLSearchParams(query).getAll("filters")).toEqual(filters.map(value => JSON.stringify(value)))
    })

    it.each(["IN", "IS_NULL"])("falls back atomically when a %s leaf has an empty array value", (operation) => {
        const filters = [{
            logical: "or",
            children: [
                {field: "labels", operation, value: []},
                {field: "namespace", operation: "EQUALS", value: "io.kestra"},
            ],
        }]

        const query = serializeQuery({filters})

        expect(new URLSearchParams(query).getAll("filters")).toEqual(filters.map(value => JSON.stringify(value)))
    })

    it("falls back atomically when a map value has no toString method", () => {
        const valueWithoutToString = Object.assign(Object.create(null), {value: "production"})
        const filters = [{
            logical: "or",
            children: [
                {field: "labels", operation: "IN", value: {environment: valueWithoutToString}},
                {field: "state", operation: "EQUALS", value: "RUNNING"},
            ],
        }]

        const query = serializeQuery({filters})

        expect(new URLSearchParams(query).getAll("filters")).toEqual(filters.map(value => JSON.stringify(value)))
    })

    it("serializes an accessor-backed map atomically from one value snapshot", () => {
        let reads = 0
        const labels = {}
        Object.defineProperty(labels, "environment", {
            enumerable: true,
            get: () => ++reads <= 2 ? "production" : undefined,
        })
        const filters = [{
            logical: "or",
            children: [
                {field: "labels", operation: "IN", value: labels},
                {field: "state", operation: "EQUALS", value: "RUNNING"},
            ],
        }]

        const params = new URLSearchParams(serializeQuery({filters}))

        expect(params.get("filters[or][0][labels][IN][environment]")).toBe("production")
        expect(params.get("filters[or][1][state][EQUALS]")).toBe("RUNNING")
    })

    it.each([
        ["a null logical child", {logical: "or", children: [null, {field: "state", operation: "EQUALS", value: "RUNNING"}]}],
        ["a string logical child", {logical: "or", children: ["invalid", {field: "state", operation: "EQUALS", value: "RUNNING"}]}],
        ["an empty logical group", {logical: "or", children: []}],
        ["an unsupported logical operator", {logical: "xor", children: [{field: "state", operation: "EQUALS", value: "RUNNING"}]}],
        ["an ambiguous leaf and logical node", {field: "state", operation: "EQUALS", logical: "or", children: [{field: "state", operation: "EQUALS", value: "RUNNING"}]}],
    ])("falls back to ordinary array serialization for %s", (_, invalidFilter) => {
        const query = serializeQuery({filters: [invalidFilter]})

        expect(new URLSearchParams(query).getAll("filters")).toEqual([JSON.stringify(invalidFilter)])
    })

    it("falls back to ordinary array serialization for a sparse filter array", () => {
        const filters = Array(2) as QueryFilter[]
        filters[1] = {field: "state", operation: "EQUALS", value: "RUNNING"}

        const query = serializeQueryFilters(filters)

        expect(new URLSearchParams(query).getAll("filters")).toEqual([
            JSON.stringify(filters[1]),
        ])
    })

    it("keeps ordinary non-filter array serialization unchanged", () => {
        const query = serializeQuery({sort: ["state:asc", "id:desc"]})

        expect(new URLSearchParams(query).getAll("sort")).toEqual(["state:asc", "id:desc"])
    })

    it.each([
        ["a leaf-shaped object", {field: "state", operation: "IS_NULL"}],
        ["a logical-shaped object", {logical: "or", children: [{field: "state", operation: "IS_NULL"}]}],
    ])("keeps ordinary object arrays under non-filter keys unchanged for %s", (_, value) => {
        const query = serializeQuery({items: [value]})

        expect(new URLSearchParams(query).getAll("items")).toEqual([JSON.stringify(value)])
    })

    it("falls back to ordinary array serialization when a value-required filter leaf has no value", () => {
        const filters = [
            {field: "state", operation: "EQUALS"},
            {field: "namespace", operation: "EQUALS", value: "io.kestra"},
        ]
        const query = serializeQuery({filters})

        expect(new URLSearchParams(query).getAll("filters")).toEqual(filters.map(value => JSON.stringify(value)))
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
