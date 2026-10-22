import {afterEach, beforeEach, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const searchLogs = vi.hoisted(() => vi.fn())

vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs, deleteLogsFromFlow: vi.fn()}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => false, hasAny: () => false, hasAnyAction: () => false}}),
}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => ({})}))
vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => ({})}))
vi.mock("../../../utils/triggers", () => ({
    searchTriggers: vi.fn().mockResolvedValue({
        total: 2,
        results: ["first", "second"].map(flowId => ({
            trigger: {id: "poll", type: "io.kestra.plugin.core.http.Trigger"},
            state: {namespace: "company.team", flowId, triggerId: "poll", logs: ["evaluated"]},
        })),
    }),
}))

import TriggersManage from "./TriggersManage.vue"

beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    sessionStorage.clear()
    searchLogs.mockReset().mockImplementation((params: {size?: number; filters?: {field: string; value?: unknown}[]}) => {
        if (params.size === 1) return Promise.resolve({results: [], total: 0})
        const flowId = params.filters?.find(filter => filter.field === "flowId")?.value
        return Promise.resolve({
            total: 1,
            results: [{
                namespace: "company.team",
                flowId,
                triggerId: "poll",
                level: "INFO",
                timestamp: "2026-06-02T08:00:00Z",
                thread: "thread",
                message: `${flowId} evaluated`,
            }],
        })
    })
})

afterEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    document.title = ""
    vi.useRealTimers()
})

it("isolates expanded logs for triggers with the same id in different flows", async () => {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name: "admin/triggers", path: "/:tenant/triggers", component: {template: "<div/>"}}],
    })
    await router.push({name: "admin/triggers", params: {tenant: "main"}})
    await router.isReady()
    const wrapper = i18nMount(TriggersManage, {
        global: {
            plugins: [createPinia(), router],
            stubs: {FlowRun: true, TopNavBar: true, AddTriggerModal: true, TestEventDialog: true},
        },
    })
    await flushPromises()
    const expanders = wrapper.findAll(".kel-table__expand-icon")
    expect(expanders).toHaveLength(2)
    await expanders[0].trigger("click")
    await expanders[1].trigger("click")
    await flushPromises()

    const panels = wrapper.findAll(".log-panel")
    expect(panels).toHaveLength(2)
    expect(panels[0].text()).toContain("first evaluated")
    expect(panels[0].text()).not.toContain("second evaluated")
    expect(panels[1].text()).toContain("second evaluated")
    expect(panels[1].text()).not.toContain("first evaluated")
    const requests = searchLogs.mock.calls.map(([params]) => params).filter(params => params.size !== 1)
    expect(requests).toHaveLength(2)
    for (const [index, request] of requests.entries()) {
        expect(request.filters).toEqual(expect.arrayContaining([
            {field: "namespace", operation: "EQUALS", value: "company.team"},
            {field: "flowId", operation: "EQUALS", value: index === 0 ? "first" : "second"},
            {field: "triggerId", operation: "EQUALS", value: "poll"},
        ]))
    }
})
