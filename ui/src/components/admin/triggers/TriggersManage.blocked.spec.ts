import {afterEach, beforeEach, expect, it, vi} from "vitest"
import {flushPromises, VueWrapper} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {i18nMount} from "../../../../tests/unit/i18nMount"

vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs: vi.fn().mockResolvedValue({results: [], total: 0}), deleteLogsFromFlow: vi.fn()}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => false, hasAny: () => false, hasAnyAction: () => false}}),
}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => ({})}))
vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => ({})}))
vi.mock("../../../utils/triggers", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../../utils/triggers")>(),
    searchTriggers: vi.fn().mockResolvedValue({
        total: 2,
        results: [
            {flowId: "by-policy", blockedBy: {reason: "POLICY", ids: ["deny-log"], message: "Log plugin is not allowed by policy"}},
            {flowId: "accepted", nextEvaluationDate: "2026-10-27T10:01:00Z"},
        ].map(({flowId, ...state}) => ({
            trigger: {id: "schedule", type: "io.kestra.plugin.core.trigger.Schedule"},
            state: {namespace: "company.team", flowId, triggerId: "schedule", ...state},
        })),
    }),
}))

import TriggersManage from "./TriggersManage.vue"

beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    sessionStorage.clear()
})

afterEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    document.title = ""
    vi.useRealTimers()
})

const blockedTooltips = (wrapper: VueWrapper, dataTest = "trigger-blocked") =>
    wrapper.findAllComponents({name: "KsTooltip"}).filter(tooltip => tooltip.find(`[data-test="${dataTest}"]`).exists())

// kestra-ee#10290: the admin Triggers page shows a governance block in place of the next evaluation date.
it("shows the block with a message on the tag and next to the id, only on the blocked trigger", async () => {
    localStorage.setItem("columns_displayTriggersColumns", "flowId,nextEvaluationDate")
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

    for (const dataTest of ["trigger-blocked", "trigger-blocked-icon"]) {
        const tooltips = blockedTooltips(wrapper, dataTest)
        expect(tooltips).toHaveLength(1)
        expect(tooltips[0].props("content")).toBeTruthy()
    }
})
