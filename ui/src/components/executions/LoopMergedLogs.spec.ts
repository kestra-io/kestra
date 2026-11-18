import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {ref, type Ref} from "vue"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import LoopMergedLogs from "./LoopMergedLogs.vue"
import {useExecutionsStore} from "../../stores/executions"

const state = vi.hoisted(() => ({
    merged: undefined as unknown as ReturnType<typeof buildMerged>,
    setEntries: vi.fn(),
    entries: undefined as unknown as Ref<{taskId: string; number: number}[]>,
}))

function buildMerged() {
    return {
        targets: ref<unknown>(),
        lines: ref<unknown[]>([]),
        total: ref(0),
        loading: ref(false),
        loadingMore: ref(false),
        loaded: ref(true),
        failure: ref<string>(),
        hasMore: ref(false),
        hasPreviousBatch: ref(false),
        hasNextBatch: ref(false),
        previousBatch: vi.fn(),
        nextBatch: vi.fn(),
        refresh: vi.fn(),
        loadMore: vi.fn(),
    }
}

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "company.team", flowId: "close_lite", id: "root"}, query: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("../../composables/useLoopScope", async () => {
    const {ref: vueRef} = await import("vue")
    return {useLoopScope: () => ({entries: state.entries, key: vueRef(""), setEntries: state.setEntries})}
})

vi.mock("../../composables/useLoopMergedLogs", () => ({useLoopMergedLogs: () => state.merged}))

const chain = [
    {id: "c13", taskId: "per_customer", number: 13, value: "13"},
    {id: "i7", taskId: "per_invoice", number: 7, value: "7"},
]

const globalConfig = {
    stubs: {
        LogLine: {props: ["log"], template: "<div data-test='log-line'>{{ log.message }}</div>"},
        DynamicScroller: {props: ["items"], template: "<div><slot v-for='item in items' :item='item' :active='true' /></div>"},
        DynamicScrollerItem: {template: "<div><slot /></div>"},
        RouterLink: {props: ["to"], template: "<a><slot /></a>"},
    },
}

const mountMerged = () => i18nMount(LoopMergedLogs, {
    props: {levelParams: {}, running: false},
    messages: {
        logs_view: {loop: {"show-in-overview-for": "Show {scope} in Overview", "batch-range": "Failed iterations {from}-{to} of {total}"}},
        "topology-graph": {loop: {"iteration-index": "index {index}", "iteration-value-index": "{value} · index {index}"}},
    },
    global: globalConfig,
})

describe("executions/LoopMergedLogs", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        useExecutionsStore().execution = {id: "root", namespace: "company.team", flowId: "close_lite", state: {current: "SUCCESS"}} as never
        state.merged = buildMerged()
        state.setEntries.mockClear()
        state.entries = ref([])
    })

    test("shouldLetTheUserClearAScopeWhoseIterationNoLongerExists", async () => {
        state.entries.value = [{taskId: "per_customer", number: 99}]
        state.merged.failure.value = "not-found"
        const wrapper = mountMerged()

        expect(wrapper.find("[data-test=loop-merged-logs-error]").exists()).toBe(true)
        expect(wrapper.find("[data-test=loop-merged-logs-retry]").exists()).toBe(false)
        await wrapper.find("[data-test=loop-merged-scope-clear]").trigger("click")
        expect(state.setEntries).toHaveBeenCalledWith([])
    })

    afterEach(() => {
        localStorage.clear()
    })

    test("shouldLabelLinesByIterationValueAndOnlyAddTheIndexWhenTheValueRepeatsInTheSameLoopRun", () => {
        const customer = (id: string, number: number) => ({id, taskId: "per_customer", number, value: "acme"})
        const invoice = (id: string, number: number, value: string) => ({id, taskId: "per_invoice", number, value})
        state.merged.targets.value = {
            chains: {
                a: [customer("c1", 1), invoice("a", 1, "INV")],
                b: [customer("c1", 1), invoice("b", 2, "INV")],
                c: [customer("c2", 2), invoice("c", 1, "INV")],
            },
            offset: 0, end: 3, total: 3, scope: [],
        }
        state.merged.lines.value = ["a", "b", "c"].map((executionId) => ({executionId, message: executionId, timestamp: "2026-10-09T09:49:15Z"}))

        const labels = mountMerged().findAll("[data-test='loop-merged-scope-button']").map((button) => button.text())

        expect(labels).toEqual([
            "per_customer: acme · index 0 › per_invoice: INV · index 0",
            "per_customer: acme · index 0 › per_invoice: INV · index 1",
            "per_customer: acme · index 1 › per_invoice: INV",
        ])
    })

    test("shouldRenderEachLineWithItsScopeAndAnAccessibleOverviewLink", () => {
        state.merged.targets.value = {chains: {i7: chain}, offset: 0, end: 1, total: 1, scope: []}
        state.merged.lines.value = [
            {executionId: "i7", message: "Task failure", timestamp: "2026-10-09T09:49:15Z"},
            {executionId: "root", message: "root line", timestamp: "2026-10-09T09:49:16Z"},
        ]

        const wrapper = mountMerged()

        const rows = wrapper.findAll("[data-test='loop-merged-row']")
        expect(rows).toHaveLength(2)
        expect(rows[0].find("[data-test='loop-merged-scope-button']").text()).toContain("per_customer")
        expect(rows[0].find("[data-test='loop-merged-overview-link']").attributes("aria-label")).toContain("per_invoice")
        expect(rows[1].find("[data-test='loop-merged-scope-button']").text()).toBe("logs_view.loop.this-execution")
        expect(rows[1].find("[data-test='loop-merged-overview-link']").exists()).toBe(false)
    })

    test("shouldWriteTheLoopScopeWhenAScopeIsClicked", async () => {
        state.merged.targets.value = {chains: {i7: chain}, offset: 0, end: 1, total: 1, scope: []}
        state.merged.lines.value = [{executionId: "i7", message: "Task failure", timestamp: "2026-10-09T09:49:15Z"}]
        const wrapper = mountMerged()

        await wrapper.find("[data-test='loop-merged-scope-button']").trigger("click")

        expect(state.setEntries).toHaveBeenCalledWith([{taskId: "per_customer", number: 13}, {taskId: "per_invoice", number: 7}])
    })

    test("shouldNotOfferToScopeALineWhoseLoopRanSeveralTimesInItsExecution", async () => {
        const multiRunChain = [{id: "c13", taskId: "per_customer", number: 13, value: "13", multiRun: true}]
        state.merged.targets.value = {chains: {c13: multiRunChain}, offset: 0, end: 1, total: 1, scope: []}
        state.merged.lines.value = [{executionId: "c13", message: "Task failure", timestamp: "2026-10-09T09:49:15Z"}]
        const wrapper = mountMerged()

        const button = wrapper.find("[data-test='loop-merged-scope-button']")
        await button.trigger("click")

        expect(button.attributes("disabled")).toBeDefined()
        expect(wrapper.find("[data-test='loop-merged-overview-link']").exists()).toBe(false)
        expect(state.setEntries).not.toHaveBeenCalled()
    })

    test("shouldShowTheRangeOfFailedIterationsOfTheCurrentBatchWhenThereAreSeveral", () => {
        state.merged.targets.value = {chains: {}, offset: 0, end: 148, total: 12480, scope: []}
        state.merged.hasNextBatch.value = true
        state.merged.lines.value = [{executionId: "root", message: "x", timestamp: "2026-10-09T09:49:16Z"}]

        const wrapper = mountMerged()

        expect(wrapper.find("[data-test='loop-merged-batch-range']").text()).toBe(`Failed iterations 1-148 of ${(12480).toLocaleString()}`)
        expect(wrapper.find("[data-test='loop-merged-batch-previous']").attributes("disabled")).toBeDefined()
        expect(wrapper.find("[data-test='loop-merged-batch-next']").attributes("disabled")).toBeUndefined()
    })

    test("shouldNavigateBetweenBatchesWithPreviousAndNext", async () => {
        state.merged.targets.value = {chains: {}, offset: 148, end: 300, total: 400, scope: []}
        state.merged.hasPreviousBatch.value = true
        state.merged.hasNextBatch.value = true
        const wrapper = mountMerged()

        await wrapper.find("[data-test='loop-merged-batch-next']").trigger("click")
        await wrapper.find("[data-test='loop-merged-batch-previous']").trigger("click")

        expect(state.merged.nextBatch).toHaveBeenCalledTimes(1)
        expect(state.merged.previousBatch).toHaveBeenCalledTimes(1)
    })

    test("shouldHideTheBatchNavigatorWhenEveryFailedIterationFitsInOneBatch", () => {
        state.merged.targets.value = {chains: {}, offset: 0, end: 3, total: 3, scope: []}
        state.merged.lines.value = [{executionId: "root", message: "x", timestamp: "2026-10-09T09:49:16Z"}]

        const wrapper = mountMerged()

        expect(wrapper.find("[data-test='loop-merged-batches']").exists()).toBe(false)
    })

    test("shouldShowTheEmptyStateWhenNothingMatches", () => {
        const wrapper = mountMerged()

        expect(wrapper.find("[data-test='loop-merged-logs-empty']").exists()).toBe(true)
    })

    test("shouldShowAnErrorWithRetryWhenTheSearchFailed", async () => {
        state.merged.failure.value = "unknown"
        const wrapper = mountMerged()

        expect(wrapper.find("[data-test='loop-merged-logs-error']").exists()).toBe(true)
        await wrapper.find("[data-test='loop-merged-logs-retry']").trigger("click")

        expect(state.merged.refresh).toHaveBeenCalled()
    })

    test("shouldShowTheLoadingStateBeforeTheFirstResponse", () => {
        state.merged.loaded.value = false
        const wrapper = mountMerged()

        expect(wrapper.find("[data-test='loop-merged-logs-loading']").exists()).toBe(true)
    })
})
