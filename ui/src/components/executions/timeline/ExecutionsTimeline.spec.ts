import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {flushPromises, VueWrapper} from "@vue/test-utils"
import {createMemoryHistory, createRouter, type Router} from "vue-router"
import {defineComponent, nextTick} from "vue"

const STATE_EXCLUDE_KEY = "filters[state][NOT_IN]"

const store = vi.hoisted(() => ({
    findExecutions: vi.fn(),
}))

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => store,
}))

const density = vi.hoisted(() => ({
    fetchDensity: vi.fn(),
    fetchRowDensity: vi.fn(),
}))

vi.mock("../../../composables/useExecutionsDensity", () => ({
    useExecutionsDensity: () => density,
}))

const filterConfig = vi.hoisted(() => ({
    value: {
        title: "",
        keys: [{key: "state"}, {key: "timeRange"}, {key: "labels"}, {key: "childFilter"}],
    },
}))

vi.mock("../../filter/configurations/executionFilter", () => ({
    useExecutionFilter: () => filterConfig,
}))

vi.mock("../../filter/configurations/flowExecutionFilter", () => ({
    useFlowExecutionFilter: () => filterConfig,
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {
        ...actual,
        dateUtils: {...actual.dateUtils, dateFilter: (value: string) => value},
    }
})

import ExecutionsTimeline from "./ExecutionsTimeline.vue"
import {MAX_RAW_EXECUTIONS, RAW_MODE_REENTRY_THRESHOLD, type RowDensityPoint} from "../../../utils/executionsTimeline"
import {START_QUERY_KEY, END_QUERY_KEY} from "../../../composables/useTimelineRange"

const messages = {
    now: "Now",
    executionsTimeline: {
        breadcrumb: {all: "All", total: "Total {count}", failed: "Failed {count}"},
        error: {title: "Error title", description: "Error description"},
        truncated: {description: "Showing {shown} of {total}"},
        empty: {description: "Empty", resetFilters: "Reset filters", widenRange: "Widen range"},
    },
}

const TimelineRowStub = defineComponent({
    name: "TimelineRow",
    props: ["label", "executions", "densityPoints", "densityGranularityMs", "total", "failed", "rangeStartMs", "rangeEndMs", "availableWidthPx", "packLanes", "dimmedStates", "drillable"],
    emits: ["drill-in", "open-own-page", "show-only-flow", "zoom-to-range"],
    template: "<div class=\"stub-row\" :data-label=\"label\" :data-total=\"total\" :data-failed=\"failed\" :data-aggregated=\"densityPoints !== undefined\" />",
})

const ChartLegendStub = defineComponent({
    name: "ChartLegend",
    props: ["items", "formatValue", "toggledOff"],
    emits: ["toggle"],
    template: `
        <div class="stub-legend">
            <button
                v-for="item in items"
                :key="item.label"
                :data-test="'legend-item-' + item.label"
                type="button"
                @click="$emit('toggle', item.label)"
            >{{ item.label }} ({{ item.count }})</button>
        </div>
    `,
})

const TimelineToolbarStub = defineComponent({
    name: "TimelineToolbar",
    props: ["rangeStartMs", "rangeEndMs", "expanded", "density", "densityGranularityMs", "densityIgnoredFilters", "scrubberWidthPx"],
    template: "<div />",
})

const stubs = {
    TimelineToolbar: TimelineToolbarStub,
    TimelineRow: TimelineRowStub,
    ChartLegend: ChartLegendStub,
    KsBreadcrumb: {props: ["items"], template: "<div />"},
    KsTag: {props: ["type"], template: "<span class=\"ks-tag\" :data-type=\"type\"><slot /></span>"},
    KsSkeleton: {props: ["loading", "rows", "animated"], template: "<div><slot /></div>"},
    KsAlert: {
        props: ["type", "title", "description", "closable"],
        template: "<div class=\"ks-alert\" :data-type=\"type\">{{ description }}</div>",
    },
    KsEmpty: {template: "<div><slot /><slot name=\"description\" /></div>"},
    KsButton: {template: "<button><slot /></button>"},
}

const EMPTY_DENSITY = {points: [], granularityMs: 60_000, ignoredFilters: []}

function rowsOf(rowPoints: RowDensityPoint[], overrides: Record<string, unknown> = {}) {
    return {rowPoints, granularityMs: 3_600_000, ignoredFilters: [], isCapped: false, ...overrides}
}

const HOUR_START_MS = Date.parse("2026-01-01T00:00:00Z")

const aggregatedRowPoints: RowDensityPoint[] = [
    {startMs: HOUR_START_MS, state: "SUCCESS", count: 3000, namespace: "company.team", flowId: "flow-a"},
    {startMs: HOUR_START_MS, state: "FAILED", count: 40, namespace: "company.team", flowId: "flow-a"},
    {startMs: HOUR_START_MS, state: "SUCCESS", count: 500, namespace: "company.other", flowId: "flow-b"},
]

function buildExecution(id: string, state: string, {namespace = "company.team", flowId = "flow-a"}: {namespace?: string; flowId?: string} = {}) {
    return {
        id,
        namespace,
        flowId,
        state: {current: state, startDate: "2026-01-01T00:00:00Z", endDate: "2026-01-01T00:01:00Z"},
    }
}

function createTestRouter(): Router {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "executions/list", path: "/:tenant?/executions", component: {template: "<div/>"}},
        ],
    })
}

async function settle() {
    await flushPromises()
    await nextTick()
    await flushPromises()
}

const DEFAULT_TIME_RANGE = {"filters[timeRange][EQUALS]": "PT24H"}

async function mountTimeline(router: Router, {query = DEFAULT_TIME_RANGE, props = {}}: {query?: Record<string, string>; props?: Record<string, unknown>} = {}) {
    await router.push({name: "executions/list", params: {tenant: "main"}, query})
    const wrapper = i18nMount(ExecutionsTimeline, {
        messages,
        props,
        global: {plugins: [router], stubs},
    })
    await settle()
    return wrapper
}

describe("ExecutionsTimeline", () => {
    let wrapper: VueWrapper | undefined

    beforeEach(() => {
        density.fetchDensity.mockResolvedValue(EMPTY_DENSITY)
        density.fetchRowDensity.mockResolvedValue(rowsOf([]))
    })

    afterEach(() => {
        wrapper?.unmount()
        store.findExecutions.mockReset()
        density.fetchDensity.mockReset()
        density.fetchRowDensity.mockReset()
        vi.useRealTimers()
    })

    it("should agree the breadcrumb failed count with the sum of per-row failed counts when a KILLING execution is present", async () => {
        const executions = [
            ...Array.from({length: 5}, (_, i) => buildExecution(`success-${i}`, "SUCCESS")),
            buildExecution("failed-1", "FAILED"),
            buildExecution("failed-2", "FAILED"),
            buildExecution("killing-1", "KILLING"),
        ]
        store.findExecutions.mockResolvedValue({results: executions, total: executions.length})

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        const failedTag = wrapper.find("[data-test=timeline-failed-count]")
        expect(failedTag.text()).toBe("Failed 3")

        const rowsFailedSum = wrapper.findAll(".stub-row")
            .reduce((sum, row) => sum + Number(row.attributes("data-failed")), 0)
        expect(rowsFailedSum).toBe(3)
    })

    it("should surface a truncation notice when the response total exceeds the fetched executions", async () => {
        const executions = [buildExecution("1", "SUCCESS"), buildExecution("2", "SUCCESS"), buildExecution("3", "SUCCESS")]
        store.findExecutions.mockResolvedValue({results: executions, total: 12})

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        const notice = wrapper.find("[data-test=timeline-truncated]")
        expect(notice.exists()).toBe(true)
        expect(notice.text()).toBe("Showing 3 of 12")
    })

    it("should not surface a truncation notice when every execution in scope was fetched", async () => {
        const executions = [buildExecution("1", "SUCCESS")]
        store.findExecutions.mockResolvedValue({results: executions, total: executions.length})

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        expect(wrapper.find("[data-test=timeline-truncated]").exists()).toBe(false)
    })

    it("should keep a toggled-off state visible in the legend and let it be re-included", async () => {
        const allExecutions = [
            ...Array.from({length: 5}, (_, i) => buildExecution(`success-${i}`, "SUCCESS")),
            buildExecution("failed-1", "FAILED"),
            buildExecution("failed-2", "FAILED"),
        ]
        store.findExecutions.mockImplementation((options: Record<string, unknown>) => {
            const excludeRaw = options[STATE_EXCLUDE_KEY] as string | undefined
            const excluded = excludeRaw ? excludeRaw.split(",") : []
            const filtered = excluded.length > 0
                ? allExecutions.filter(execution => !excluded.includes(execution.state.current))
                : allExecutions
            return Promise.resolve({results: filtered, total: filtered.length})
        })

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        expect(wrapper.find("[data-test=\"legend-item-FAILED\"]").text()).toBe("FAILED (2)")
        expect(wrapper.find(".stub-row").attributes("data-total")).toBe("7")

        await wrapper.get("[data-test=\"legend-item-FAILED\"]").trigger("click")
        await settle()

        expect(wrapper.find(".stub-row").attributes("data-total")).toBe("5")
        expect(wrapper.find("[data-test=\"legend-item-FAILED\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"legend-item-FAILED\"]").text()).toBe("FAILED (2)")

        await wrapper.get("[data-test=\"legend-item-FAILED\"]").trigger("click")
        await settle()

        expect(wrapper.find(".stub-row").attributes("data-total")).toBe("7")
    })

    it("should size its window from the filter bar's relative time range", async () => {
        store.findExecutions.mockResolvedValue({results: [], total: 0})

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {query: {"filters[timeRange][EQUALS]": "PT1H"}})

        const toolbar = wrapper.getComponent(TimelineToolbarStub)
        expect(toolbar.props("rangeEndMs") - toolbar.props("rangeStartMs")).toBe(60 * 60 * 1000)
    })

    it("should scope its query with the labels and child filter the table is scoped with", async () => {
        store.findExecutions.mockResolvedValue({results: [], total: 0})

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {props: {labels: {team: "red"}, childFilter: "MAIN"}})

        const mainQuery = store.findExecutions.mock.calls[0][0] as Record<string, unknown>
        expect(mainQuery["filters[labels][EQUALS][team]"]).toBe("red")
        expect(mainQuery["filters[childFilter][EQUALS]"]).toBe("MAIN")
    })

    it("should keep the newest response when an older one resolves after it", async () => {
        const resolvers: Array<(value: unknown) => void> = []
        store.findExecutions.mockImplementation(() => new Promise(resolve => resolvers.push(resolve)))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)
        const [staleMain] = resolvers.splice(0)

        await router.push({query: {"filters[timeRange][EQUALS]": "PT1H"}})
        await settle()
        const [freshMain] = resolvers.splice(0)

        freshMain({results: [buildExecution("fresh", "SUCCESS")], total: 1})
        await settle()
        staleMain({results: [buildExecution("stale-1", "FAILED"), buildExecution("stale-2", "FAILED")], total: 2})
        await settle()

        expect(wrapper.find(".stub-row").attributes("data-total")).toBe("1")
        expect(wrapper.find("[data-test=timeline-failed-count]").exists()).toBe(false)
    })

    it("should refetch on reload even when the route has not changed", async () => {
        store.findExecutions.mockResolvedValue({results: [], total: 0})

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {query: {"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-01-01T00:00:00.000Z", "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-01-02T00:00:00.000Z"}})
        const callsBeforeReload = store.findExecutions.mock.calls.length

        const exposed = wrapper.vm as unknown as {reload: () => void}
        exposed.reload()
        await settle()

        expect(store.findExecutions.mock.calls.length).toBeGreaterThan(callsBeforeReload)
    })

    it("should draw the flows of child namespaces at the namespace level, as the prefix query fetches them", async () => {
        store.findExecutions.mockResolvedValue({
            results: [
                buildExecution("parent", "SUCCESS", {namespace: "company", flowId: "flow-a"}),
                buildExecution("child-1", "FAILED", {namespace: "company.team", flowId: "flow-b"}),
                buildExecution("child-2", "SUCCESS", {namespace: "company.team", flowId: "flow-b"}),
            ],
            total: 3,
        })

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {props: {namespace: "company"}})

        const rows = wrapper.findAll(".stub-row").map(row => [row.attributes("data-label"), row.attributes("data-total")])
        expect(rows).toEqual(expect.arrayContaining([["flow-a", "1"], ["team.flow-b", "2"]]))
        expect(rows).toHaveLength(2)
    })

    it("should scope a drill into a child-namespace flow to that flow's own namespace", async () => {
        store.findExecutions.mockResolvedValue({
            results: [buildExecution("child", "SUCCESS", {namespace: "company.team", flowId: "flow-b"})],
            total: 1,
        })

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {props: {namespace: "company"}})
        wrapper.getComponent(TimelineRowStub).vm.$emit("drill-in")
        await settle()

        expect(wrapper.emitted("update:drillNamespace")?.at(-1)).toEqual(["company.team"])
        expect(wrapper.emitted("update:drillFlowId")?.at(-1)).toEqual(["flow-b"])
    })

    it("should hand the reset to the page so it can restore the filter bar's defaults", async () => {
        store.findExecutions.mockResolvedValue({results: [], total: 0})

        const router = createTestRouter()
        wrapper = await mountTimeline(router)
        await wrapper.findAll("button").find(button => button.text() === "Reset filters")!.trigger("click")

        expect(wrapper.emitted("reset-filters")).toHaveLength(1)
    })

    it("should hold its first load until the page's default time range lands in the URL", async () => {
        store.findExecutions.mockResolvedValue({results: [], total: 0})

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {query: {}})
        expect(store.findExecutions).not.toHaveBeenCalled()

        await router.replace({query: DEFAULT_TIME_RANGE})
        await settle()

        expect(store.findExecutions).toHaveBeenCalledTimes(1)
        expect(store.findExecutions.mock.calls[0][0]).toMatchObject(DEFAULT_TIME_RANGE)
    })

    it("should load anyway when no default time range ever lands", async () => {
        vi.useFakeTimers()
        store.findExecutions.mockResolvedValue({results: [], total: 0})

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {query: {}})
        expect(store.findExecutions).not.toHaveBeenCalled()

        await vi.advanceTimersByTimeAsync(2000)

        expect(store.findExecutions).toHaveBeenCalledTimes(1)
    })

    it("should feed the density bar from the aggregate over the context window, not from the capped rows", async () => {
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 5000})
        density.fetchDensity.mockResolvedValue({points: [{startMs: 1, state: "SUCCESS", count: 5000}], granularityMs: 3_600_000, ignoredFilters: ["childFilter"]})

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        const [query, startMs, endMs] = density.fetchDensity.mock.calls[0]
        const toolbar = wrapper.getComponent(TimelineToolbarStub)
        expect(query).toMatchObject(DEFAULT_TIME_RANGE)
        expect(endMs - startMs).toBe(2 * (toolbar.props("rangeEndMs") - toolbar.props("rangeStartMs")))
        expect(toolbar.props("density")).toEqual([{startMs: 1, state: "SUCCESS", count: 5000}])
        expect(toolbar.props("densityGranularityMs")).toBe(3_600_000)
        expect(toolbar.props("densityIgnoredFilters")).toEqual(["childFilter"])
    })

    it("should draw accurate per-namespace rows from the aggregated counts of the visible range when more executions ran than the raw fetch can hold", async () => {
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 3540})
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        const rows = wrapper.findAll(".stub-row").map(row => [row.attributes("data-label"), row.attributes("data-total"), row.attributes("data-failed"), row.attributes("data-aggregated")])
        expect(rows).toEqual([["company.team", "3040", "40", "true"], ["company.other", "500", "0", "true"]])
        expect(wrapper.find("[data-test=timeline-truncated]").exists()).toBe(false)
        expect(wrapper.find(".ks-tag").text()).toBe("Total 3540")
        expect(wrapper.find("[data-test=timeline-failed-count]").text()).toBe("Failed 40")
        expect(wrapper.find("[data-test=\"legend-item-SUCCESS\"]").text()).toBe("SUCCESS (3500)")

        const toolbar = wrapper.getComponent(TimelineToolbarStub)
        const [, startMs, endMs] = density.fetchRowDensity.mock.calls[0]
        expect([startMs, endMs]).toEqual([toolbar.props("rangeStartMs"), toolbar.props("rangeEndMs")])
    })

    it("should draw the flows of child namespaces at the namespace level from the aggregated counts", async () => {
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 3540})
        density.fetchRowDensity.mockResolvedValue(rowsOf([
            {startMs: HOUR_START_MS, state: "SUCCESS", count: 2000, namespace: "company", flowId: "flow-a"},
            {startMs: HOUR_START_MS, state: "SUCCESS", count: 1500, namespace: "company.team", flowId: "flow-b"},
        ]))

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {props: {namespace: "company"}})

        const rows = wrapper.findAll(".stub-row").map(row => [row.attributes("data-label"), row.attributes("data-total")])
        expect(rows).toEqual([["flow-a", "2000"], ["team.flow-b", "1500"]])
    })

    it("should keep drawing raw executions, without any aggregated request, when the raw fetch holds all of them", async () => {
        const executions = Array.from({length: MAX_RAW_EXECUTIONS}, (_, i) => buildExecution(`exec-${i}`, "SUCCESS"))
        store.findExecutions.mockResolvedValue({results: executions, total: MAX_RAW_EXECUTIONS})
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("false")
        expect(wrapper.find("[data-test=timeline-truncated]").exists()).toBe(false)
        expect(density.fetchRowDensity).not.toHaveBeenCalled()
    })

    it("should stay raw and keep the truncation notice when the aggregate ignores an active filter", async () => {
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 3540})
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints, {ignoredFilters: ["q"]}))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("false")
        expect(wrapper.find("[data-test=timeline-truncated]").text()).toBe("Showing 1 of 3540")
    })

    it("should stay raw, keep the notice and leave the toolbar density untouched when the aggregate hit its row cap", async () => {
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 3540})
        const toolbarPoints = [{startMs: 1, state: "SUCCESS", count: 3540}]
        density.fetchDensity.mockResolvedValue({...EMPTY_DENSITY, points: toolbarPoints})
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints, {isCapped: true}))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("false")
        expect(wrapper.find("[data-test=timeline-truncated]").exists()).toBe(true)
        expect(wrapper.getComponent(TimelineToolbarStub).props("density")).toEqual(toolbarPoints)
    })

    it("should stay raw with the truncation notice, not an error, when the aggregate request fails", async () => {
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 3540})
        density.fetchRowDensity.mockRejectedValue(new Error("too_many_buckets"))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("false")
        expect(wrapper.find("[data-test=timeline-truncated]").exists()).toBe(true)
        expect(wrapper.find(".ks-alert[data-type=error]").exists()).toBe(false)
        consoleError.mockRestore()
    })

    it("should only return to raw bars once the zoomed window is clearly below the raw fetch limit", async () => {
        const totals = [3540, RAW_MODE_REENTRY_THRESHOLD + 50, RAW_MODE_REENTRY_THRESHOLD]
        store.findExecutions.mockImplementation(() => Promise.resolve({results: [buildExecution("1", "SUCCESS")], total: totals.shift()}))
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)
        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("true")

        await router.push({query: {"filters[timeRange][EQUALS]": "PT12H"}})
        await settle()
        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("true")

        await router.push({query: {"filters[timeRange][EQUALS]": "PT6H"}})
        await settle()
        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("false")
    })

    it("should render raw, and not stick to aggregated, after an unusable aggregate once the total drops below the raw limit", async () => {
        const totals = [3540, RAW_MODE_REENTRY_THRESHOLD + 50]
        store.findExecutions.mockImplementation(() => Promise.resolve({results: [buildExecution("1", "SUCCESS")], total: totals.shift()}))
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints, {isCapped: true}))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)
        await router.push({query: {"filters[timeRange][EQUALS]": "PT12H"}})
        await settle()

        expect(wrapper.find(".stub-row").attributes("data-aggregated")).toBe("false")
        expect(density.fetchRowDensity).toHaveBeenCalledTimes(1)
    })

    it("should zoom the timeline to the bucket range when an aggregated bucket is clicked", async () => {
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 3540})
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)
        const endMs = HOUR_START_MS + 3_600_000
        wrapper.getComponent(TimelineRowStub).vm.$emit("zoom-to-range", {startMs: HOUR_START_MS, endMs})
        await settle()

        expect(router.currentRoute.value.query[START_QUERY_KEY]).toBe(new Date(HOUR_START_MS).toISOString())
        expect(router.currentRoute.value.query[END_QUERY_KEY]).toBe(new Date(endMs).toISOString())
    })

    it("should derive the rows and the legend of a toggled-off state from one aggregated fetch", async () => {
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 3540})
        density.fetchRowDensity.mockResolvedValue(rowsOf(aggregatedRowPoints))

        const router = createTestRouter()
        wrapper = await mountTimeline(router, {query: {...DEFAULT_TIME_RANGE, [STATE_EXCLUDE_KEY]: "FAILED"}})

        expect(density.fetchRowDensity).toHaveBeenCalledTimes(1)
        expect(density.fetchRowDensity.mock.calls[0][0]).not.toHaveProperty(STATE_EXCLUDE_KEY)
        expect(wrapper.findAll(".stub-row").map(row => row.attributes("data-total"))).toEqual(["3000", "500"])
        expect(wrapper.find("[data-test=timeline-failed-count]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"legend-item-FAILED\"]").text()).toBe("FAILED (40)")
    })

    it("should drop the toolbar density of the previous filters when the density request fails", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {})
        store.findExecutions.mockResolvedValue({results: [buildExecution("1", "SUCCESS")], total: 1})
        density.fetchDensity.mockResolvedValueOnce({...EMPTY_DENSITY, points: [{startMs: 1, state: "SUCCESS", count: 1}]})
        density.fetchDensity.mockRejectedValueOnce(new Error("boom"))

        const router = createTestRouter()
        wrapper = await mountTimeline(router)
        expect(wrapper.getComponent(TimelineToolbarStub).props("density")).toHaveLength(1)

        await router.push({query: {"filters[timeRange][EQUALS]": "PT12H"}})
        await settle()

        expect(wrapper.getComponent(TimelineToolbarStub).props("density")).toEqual([])
        expect(wrapper.find(".stub-row").exists()).toBe(true)
        vi.mocked(console.error).mockRestore()
    })
})
