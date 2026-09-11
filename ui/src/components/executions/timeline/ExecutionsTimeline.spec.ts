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
        keys: [{key: "state"}],
    },
}))

vi.mock("../../filter/configurations", () => ({
    useExecutionFilter: () => filterConfig,
    useFlowExecutionFilter: () => filterConfig,
}))

// dateUtils.dateFilter reads Vue's $moment global property, wired up by the app plugin at bootstrap
// and absent in a bare component mount; stub it with a deterministic formatter instead.
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
    props: ["items", "formatValue"],
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

const stubs = {
    TimelineToolbar: {template: "<div />"},
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

async function mountTimeline(router: Router) {
    await router.push({name: "executions/list", params: {tenant: "main"}})
    const wrapper = mount(ExecutionsTimeline, {
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

        const failedTag = wrapper.find(".ks-tag[data-type=\"danger\"]")
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

        const notice = wrapper.find(".ks-alert[data-type=\"warning\"]")
        expect(notice.exists()).toBe(true)
        expect(notice.text()).toBe("Showing 3 of 12")
    })

    it("should not surface a truncation notice when every execution in scope was fetched", async () => {
        const executions = [buildExecution("1", "SUCCESS")]
        store.findExecutions.mockResolvedValue({results: executions, total: executions.length})

        const router = createTestRouter()
        wrapper = await mountTimeline(router)

        expect(wrapper.find(".ks-alert[data-type=\"warning\"]").exists()).toBe(false)
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
})
