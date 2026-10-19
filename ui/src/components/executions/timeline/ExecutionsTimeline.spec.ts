import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, VueWrapper} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createMemoryHistory, createRouter, type Router} from "vue-router"
import {defineComponent, nextTick} from "vue"

const STATE_EXCLUDE_KEY = "filters[state][NOT_IN]"

const store = vi.hoisted(() => ({
    findExecutions: vi.fn(),
}))

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => store,
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

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {
        en: {
            now: "Now",
            executionsTimeline: {
                breadcrumb: {all: "All", total: "Total {count}", failed: "Failed {count}"},
                error: {title: "Error title", description: "Error description"},
                truncated: {description: "Showing {shown} of {total}"},
                empty: {description: "Empty", resetFilters: "Reset filters", widenRange: "Widen range"},
            },
        },
    },
})

const TimelineRowStub = defineComponent({
    name: "TimelineRow",
    props: ["label", "executions", "total", "failed", "rangeStartMs", "rangeEndMs", "availableWidthPx", "packLanes", "dimmedStates"],
    template: "<div class=\"stub-row\" :data-label=\"label\" :data-total=\"total\" :data-failed=\"failed\" />",
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
    props: ["rangeStartMs", "rangeEndMs", "expanded", "domainExecutions", "scrubberWidthPx"],
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

function buildExecution(id: string, state: string) {
    return {
        id,
        namespace: "company.team",
        flowId: "flow-a",
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

async function mountTimeline(router: Router, {query = {}, props = {}}: {query?: Record<string, string>; props?: Record<string, unknown>} = {}) {
    await router.push({name: "executions/list", params: {tenant: "main"}, query})
    const wrapper = mount(ExecutionsTimeline, {
        props,
        global: {plugins: [i18n, router], stubs},
    })
    await settle()
    return wrapper
}

describe("ExecutionsTimeline", () => {
    let wrapper: VueWrapper | undefined

    afterEach(() => {
        wrapper?.unmount()
        store.findExecutions.mockReset()
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
        const [staleMain, staleDomain] = resolvers.splice(0)

        await router.push({query: {"filters[timeRange][EQUALS]": "PT1H"}})
        await settle()
        const [freshMain, freshDomain] = resolvers.splice(0)

        freshMain({results: [buildExecution("fresh", "SUCCESS")], total: 1})
        freshDomain({results: [], total: 0})
        await settle()
        staleMain({results: [buildExecution("stale-1", "FAILED"), buildExecution("stale-2", "FAILED")], total: 2})
        staleDomain({results: [], total: 0})
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
})
