import {describe, test, expect, vi, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const miscState = vi.hoisted(() => ({configs: undefined as Record<string, any> | undefined}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: miscState.configs}),
}))

const mockPush = vi.fn()
vi.mock("vue-router", () => ({
    useRouter: () => ({push: mockPush}),
    useRoute: () => ({params: {tenant: "main"}}),
}))

import {useChartDrillDown, registerDrillDown} from "../../../src/components/dashboard/composables/chartDrillDown"
import {registerDrillDownPreview} from "../../../src/components/dashboard/composables/drillDownPreview"
import {useDrillDownStore} from "../../../src/stores/drillDown"

const chartFor = (type: string) => ({data: {type: `io.kestra.plugin.x.dashboard.data.${type}`}})

describe("useChartDrillDown().drillDown routing", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        mockPush.mockClear()
        miscState.configs = undefined
    })

    test("opens the preview drawer (no redirect) for a route with a table preview", () => {
        registerDrillDown("RoutingTestTable", {
            route: "test/routing-table/list",
            fieldKey: {X: "x"},
            multiSelect: [],
            timeFiltered: false,
        })
        registerDrillDownPreview("test/routing-table/list", {
            mode: "table",
            columns: [],
            fetch: async () => ({results: [], total: 0}),
            rowDetail: () => ({name: "x"}),
        })

        const {drillDown} = useChartDrillDown(chartFor("RoutingTestTable"))
        drillDown([{column: {field: "X"}, value: "v"}])

        expect(mockPush).not.toHaveBeenCalled()
        const store = useDrillDownStore()
        expect(store.isOpen).toBe(true)
        expect(store.target).toEqual({
            name: "test/routing-table/list",
            timeFiltered: false,
            query: {"filters[x][EQUALS]": "v"},
        })
    })

    test("opens the preview drawer (no redirect) for a route with a logs preview", () => {
        registerDrillDown("RoutingTestLogs", {
            route: "test/routing-logs/list",
            fieldKey: {X: "x"},
            multiSelect: [],
            timeFiltered: true,
        })
        registerDrillDownPreview("test/routing-logs/list", {mode: "logs"})

        const {drillDown} = useChartDrillDown(chartFor("RoutingTestLogs"))
        drillDown([{column: {field: "X"}, value: "v"}])

        expect(mockPush).not.toHaveBeenCalled()
        expect(useDrillDownStore().isOpen).toBe(true)
    })

    test("keeps the full-page redirect for an explicit {mode: \"none\"} opt-out, without a console error", () => {
        registerDrillDown("RoutingTestNone", {
            route: "test/routing-none/list",
            fieldKey: {X: "x"},
            multiSelect: [],
            timeFiltered: false,
        })
        registerDrillDownPreview("test/routing-none/list", {mode: "none"})
        const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {})

        const {drillDown} = useChartDrillDown(chartFor("RoutingTestNone"))
        drillDown([{column: {field: "X"}, value: "v"}])

        expect(errorSpy).not.toHaveBeenCalled()
        expect(mockPush).toHaveBeenCalledWith({
            name: "test/routing-none/list",
            params: {tenant: "main"},
            query: {"filters[x][EQUALS]": "v", scope: "USER", size: 100, page: 1},
        })
        expect(useDrillDownStore().isOpen).toBe(false)

        errorSpy.mockRestore()
    })

    test("keeps the full-page redirect and logs a dev error when no preview is registered at all", () => {
        registerDrillDown("RoutingTestMissing", {
            route: "test/routing-missing/list",
            fieldKey: {X: "x"},
            multiSelect: [],
            timeFiltered: false,
        })
        // Deliberately no registerDrillDownPreview call for this route.
        const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {})

        const {drillDown} = useChartDrillDown(chartFor("RoutingTestMissing"))
        drillDown([{column: {field: "X"}, value: "v"}])

        expect(errorSpy).toHaveBeenCalledTimes(1)
        expect(errorSpy.mock.calls[0][0]).toContain("test/routing-missing/list")
        expect(mockPush).toHaveBeenCalledWith(expect.objectContaining({name: "test/routing-missing/list"}))
        expect(useDrillDownStore().isOpen).toBe(false)

        errorSpy.mockRestore()
    })

    test("returns without pushing or opening when the chart has no drill-down descriptor", () => {
        const {drillDown} = useChartDrillDown(chartFor("Metrics"))
        drillDown([{column: {field: "X"}, value: "v"}])

        expect(mockPush).not.toHaveBeenCalled()
        expect(useDrillDownStore().isOpen).toBe(false)
    })
})
