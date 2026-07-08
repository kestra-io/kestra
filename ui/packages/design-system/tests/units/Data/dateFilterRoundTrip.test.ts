import {describe, test, expect} from "vitest"
import {encodeFiltersToQuery, keyOfComparator} from "../../../src/components/Data/KsDataTable/filter/utils/helpers"
import {parseEncodedGroups} from "../../../src/components/Data/KsDataTable/filter/utils/routeDecoder"
import {Comparators, type FilterConfiguration} from "../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

// Per-field date chips: each date field is its own `time-range` chip that accepts a relative
// ISO-8601 duration or an absolute range, encoded directly onto the real backend field.
const CONFIG: FilterConfiguration = {
    keys: [
        {
            key: "startDate",
            label: "Start date",
            comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO],
            valueType: "time-range",
        },
        {
            key: "nextExecutionDate",
            label: "Next execution date",
            comparators: [Comparators.LESS_THAN_OR_EQUAL_TO],
            valueType: "time-range",
        },
    ],
} as unknown as FilterConfiguration

const encode = (filters: any[]) => encodeFiltersToQuery(filters, keyOfComparator)

describe("date-field relative-duration encoding", () => {
    test("encodes a relative duration onto the field with its own operation", () => {
        expect(encode([
            {key: "startDate", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: "PT24H"},
        ])).toEqual({"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "PT24H"})
    })

    test("future-oriented field uses an upper bound", () => {
        expect(encode([
            {key: "nextExecutionDate", comparator: Comparators.LESS_THAN_OR_EQUAL_TO, value: "P1D"},
        ])).toEqual({"filters[nextExecutionDate][LESS_THAN_OR_EQUAL_TO]": "P1D"})
    })

    test("encodes an absolute range as a GTE/LTE pair on the same field", () => {
        const start = new Date("2024-01-01T00:00:00.000Z")
        const end = new Date("2024-01-02T00:00:00.000Z")
        expect(encode([
            {key: "startDate", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: {startDate: start, endDate: end}},
        ])).toEqual({
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": start.toISOString(),
            "filters[startDate][LESS_THAN_OR_EQUAL_TO]": end.toISOString(),
        })
    })
})

describe("date-field decoding", () => {
    test("decodes a relative duration into a single chip", () => {
        const {groups} = parseEncodedGroups(
            {"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "PT24H"},
            CONFIG,
        )
        const filters = (groups[0] as any).filters
        expect(filters).toHaveLength(1)
        expect(filters[0].key).toBe("startDate")
        expect(filters[0].value).toBe("PT24H")
    })

    test("merges an absolute GTE/LTE pair into one range chip", () => {
        const {groups} = parseEncodedGroups(
            {
                "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2024-01-01T00:00:00.000Z",
                "filters[startDate][LESS_THAN_OR_EQUAL_TO]": "2024-01-02T00:00:00.000Z",
            },
            CONFIG,
        )
        const filters = (groups[0] as any).filters
        expect(filters).toHaveLength(1)
        expect(filters[0].key).toBe("startDate")
        expect(filters[0].value).toHaveProperty("startDate")
        expect(filters[0].value).toHaveProperty("endDate")
    })
})

describe("date-field round trip", () => {
    test.each([
        ["startDate", Comparators.GREATER_THAN_OR_EQUAL_TO, "PT24H"],
        ["nextExecutionDate", Comparators.LESS_THAN_OR_EQUAL_TO, "P1D"],
    ])("survives encode → decode for %s", (key, comparator, duration) => {
        const query = encode([{key, comparator, value: duration}])
        const {groups} = parseEncodedGroups(query, CONFIG)
        const filters = (groups[0] as any).filters
        expect(filters[0].key).toBe(key)
        expect(filters[0].value).toBe(duration)
    })
})
