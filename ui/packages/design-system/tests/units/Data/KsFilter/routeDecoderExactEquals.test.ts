import {describe, test, expect} from "vitest"
import {parseEncodedGroups} from "../../../../src/components/Data/KsDataTable/filter/utils/routeDecoder"
import {encodeFilterGroupsToQuery, keyOfComparator} from "../../../../src/components/Data/KsDataTable/filter/utils/helpers"
import {
    Comparators,
    type AppliedFilter,
    type FilterConfiguration,
} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const config: FilterConfiguration = {
    title: "",
    keys: [
        {
            key: "level",
            label: "Log Level",
            comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.EQUALS],
            exactEquals: true,
            valueType: "select",
        },
    ],
}

const leafFilters = (query: Record<string, string>): AppliedFilter[] => {
    const {groups} = parseEncodedGroups(query, config)
    const leaf = groups[0]
    return leaf && "filters" in leaf ? leaf.filters : []
}

describe("routeDecoder exactEquals", () => {
    test("renders an EQUALS value as one exact chip", () => {
        const filters = leafFilters({"filters[level][EQUALS]": "DEBUG"})

        expect(filters).toHaveLength(1)
        expect(filters[0]).toMatchObject({
            key: "level",
            comparator: Comparators.EQUALS,
            value: "DEBUG",
        })
    })

    test("keeps a single >= bound as a threshold chip", () => {
        const filters = leafFilters({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO"})

        expect(filters).toHaveLength(1)
        expect(filters[0]).toMatchObject({
            key: "level",
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            value: "INFO",
        })
    })

    test("round-trips an exact chip back to a single EQUALS key", () => {
        const {groups} = parseEncodedGroups({"filters[level][EQUALS]": "DEBUG"}, config)

        const query = encodeFilterGroupsToQuery(groups, keyOfComparator)

        expect(query).toEqual({"filters[level][EQUALS]": "DEBUG"})
    })
})
