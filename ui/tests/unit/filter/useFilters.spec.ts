import {beforeEach, describe, expect, test, vi} from "vitest"
import {effectScope} from "vue"
import {useFilters} from "../../../src/components/filter/composables/useFilters"
import {ALL_TIME_RANGE, Comparators, FilterConfiguration} from "../../../src/components/filter/utils/filterTypes"

const routerPush = vi.fn()
const currentRoute = {query: {} as Record<string, string>}

vi.mock("vue-router", () => ({
    useRoute: () => currentRoute,
    useRouter: () => ({push: routerPush, replace: vi.fn(() => Promise.resolve())}),
}))

vi.mock("../../../src/components/filter/composables/useDefaultFilter", () => ({
    useDefaultFilter: vi.fn(),
    applyDefaultFilters: vi.fn(() => ({query: {}, change: false})),
}))

const configuration: FilterConfiguration = {
    title: "Executions",
    keys: [
        {
            key: "timeRange",
            label: "Interval",
            comparators: [Comparators.EQUALS],
            valueType: "select",
            clearedValue: ALL_TIME_RANGE,
        },
        {
            key: "state",
            label: "State",
            comparators: [Comparators.IN],
            valueType: "multi-select",
        },
    ],
}

const lockedConfiguration: FilterConfiguration = {
    ...configuration,
    keys: configuration.keys.map(key => key.key === "timeRange" ? {...key, clearedValue: "PT24H", removable: false} : key),
}

const setup = (config = configuration) => {
    let filters!: ReturnType<typeof useFilters>
    effectScope().run(() => {
        filters = useFilters(config, false, false, false, false)
    })
    return filters
}

const lastPushedQuery = () => routerPush.mock.calls.at(-1)?.[0]?.query

describe("useFilters - clearedValue", () => {
    beforeEach(() => {
        routerPush.mockReset()
        currentRoute.query = {
            "filters[timeRange][EQUALS]": "PT24H",
            "filters[state][IN]": "FAILED",
        }
    })

    test("removeFilter replaces the removed chip with its clearedValue", () => {
        const filters = setup()
        const timeRange = filters.appliedFilters.value.find(f => f.key === "timeRange")!

        filters.removeFilter(timeRange.id)

        expect(filters.appliedFilters.value.find(f => f.key === "timeRange")?.value).toBe(ALL_TIME_RANGE)
        expect(lastPushedQuery()).toMatchObject({"filters[timeRange][EQUALS]": ALL_TIME_RANGE})
    })

    test("clearFilters keeps a chip carrying the clearedValue", () => {
        const filters = setup()

        filters.clearFilters()

        expect(filters.appliedFilters.value.map(f => [f.key, f.value])).toEqual([["timeRange", ALL_TIME_RANGE]])
        expect(lastPushedQuery()).toEqual({"filters[timeRange][EQUALS]": ALL_TIME_RANGE})
    })
})

describe("useFilters - non-removable key", () => {
    beforeEach(() => {
        routerPush.mockReset()
        currentRoute.query = {
            "filters[timeRange][EQUALS]": "PT168H",
            "filters[state][IN]": "FAILED",
        }
    })

    test("removeFilter leaves a non-removable chip in place", () => {
        const filters = setup(lockedConfiguration)
        const timeRange = filters.appliedFilters.value.find(f => f.key === "timeRange")!

        filters.removeFilter(timeRange.id)

        expect(filters.appliedFilters.value.find(f => f.key === "timeRange")?.value).toBe("PT168H")
        expect(routerPush).not.toHaveBeenCalled()
    })

    test("clearFilters resets a non-removable chip to its clearedValue", () => {
        const filters = setup(lockedConfiguration)

        filters.clearFilters()

        expect(filters.appliedFilters.value.map(f => [f.key, f.value])).toEqual([["timeRange", "PT24H"]])
        expect(lastPushedQuery()).toEqual({"filters[timeRange][EQUALS]": "PT24H"})
    })
})
