import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {ref} from "vue"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import LoopMergedLogs from "./LoopMergedLogs.vue"
import {useExecutionsStore} from "../../stores/executions"

const state = vi.hoisted(() => ({
    merged: undefined as unknown as ReturnType<typeof buildMerged>,
    setEntries: vi.fn(),
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
    return {useLoopScope: () => ({entries: vueRef([]), key: vueRef(""), setEntries: state.setEntries})}
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
    messages: {logs_view: {loop: {"show-in-overview-for": "Show {scope} in Overview"}}},
    global: globalConfig,
})

describe("executions/LoopMergedLogs", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        useExecutionsStore().execution = {id: "root", namespace: "company.team", flowId: "close_lite", state: {current: "SUCCESS"}} as never
        state.merged = buildMerged()
        state.setEntries.mockClear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    test("shouldRenderEachLineWithItsScopeAndAnAccessibleOverviewLink", () => {
        state.merged.targets.value = {chains: {i7: chain}, truncated: false, failedShown: 1, scope: []}
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
        state.merged.targets.value = {chains: {i7: chain}, truncated: false, failedShown: 1, scope: []}
        state.merged.lines.value = [{executionId: "i7", message: "Task failure", timestamp: "2026-10-09T09:49:15Z"}]
        const wrapper = mountMerged()

        await wrapper.find("[data-test='loop-merged-scope-button']").trigger("click")

        expect(state.setEntries).toHaveBeenCalledWith([{taskId: "per_customer", number: 13}, {taskId: "per_invoice", number: 7}])
    })

    test("shouldShowTheTruncationNoticeWhenTheFailedIterationsWereCapped", () => {
        state.merged.targets.value = {chains: {}, truncated: true, failedShown: 42, scope: []}
        state.merged.lines.value = [{executionId: "root", message: "x", timestamp: "2026-10-09T09:49:16Z"}]

        const wrapper = mountMerged()

        expect(wrapper.find("[data-test='loop-merged-logs-truncated']").exists()).toBe(true)
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
