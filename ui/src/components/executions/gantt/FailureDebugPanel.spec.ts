import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import type {Execution} from "../../../stores/executions"

const store = vi.hoisted(() => ({
    executions: {} as Record<string, unknown>,
}))
const mocks = vi.hoisted(() => ({
    flow: vi.fn(),
}))

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => store.executions,
}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {hasAny: () => true, isAllowed: () => true}}),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({promptCopilot: () => undefined}),
}))
vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "company.analytics", flowId: "daily_sales_sync", tenant: "main"}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    flow: mocks.flow,
}))

import FailureDebugPanel from "./FailureDebugPanel.vue"
import FailureMiniTimeline from "./FailureMiniTimeline.vue"
import en from "../../../translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

function history(state: string, date: string) {
    return {state, date}
}

function taskRun(id: string, taskId: string, state: string, startDate: string) {
    return {
        id,
        taskId,
        state: {current: state, histories: [history("RUNNING", startDate), history(state, startDate)]},
    }
}

function mountPanel(execution: Record<string, unknown>) {
    return mount(FailureDebugPanel, {
        props: {execution: execution as unknown as Execution},
        slots: {
            default: `
                <template #default="{shouldRender, isOpen, reopen, setReopenRef}">
                    <span v-if="shouldRender && !isOpen" class="failure-debug-reopen" :ref="setReopenRef">
                        <button type="button" @click="reopen">Debug this failure</button>
                    </span>
                </template>
            `,
        },
        global: {
            plugins: [i18n],
            stubs: {
                Restart: true,
                FailureMiniTimeline: {
                    name: "FailureMiniTimeline",
                    props: ["nodes", "focusedId"],
                    emits: ["focus-task", "select-range"],
                    template: "<div />",
                    methods: {clearSelection() {}},
                },
                FailureResolvedConfig: true,
                FailureExecutionInputs: true,
                FailureUpstreamOutputs: true,
                FailureStructuralImpact: true,
                FailureLogPanel: true,
                KsExecutionStatus: true,
                KsButton: {template: "<button><slot /></button>"},
                KsIconButton: {template: "<button><slot /></button>"},
            },
        },
        attachTo: document.body,
    })
}

describe("FailureDebugPanel", () => {
    afterAll(() => {
        localStorage.clear()
    })

    beforeEach(() => {
        store.executions = {loadLogs: vi.fn().mockResolvedValue([])}
        mocks.flow.mockReset().mockResolvedValue({source: "id: x\nnamespace: y\ntasks: []\n"})
    })

    it("should not render when the execution has no failed task run", () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "SUCCESS", "2024-01-01T00:00:00Z")],
        })

        expect(wrapper.find(".failure-debug-panel").exists()).toBe(false)
    })

    it("should never render for a task that failed then succeeded on retry", () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "SUCCESS"},
            taskRunList: [taskRun("tr-1", "task-1", "SUCCESS", "2024-01-01T00:00:00Z")],
        })

        expect(wrapper.find(".failure-debug-panel").exists()).toBe(false)
    })

    it("should auto-focus the earliest failure when there are multiple simultaneous failures", () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [
                taskRun("tr-2", "task-2", "FAILED", "2024-01-01T00:00:10Z"),
                taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z"),
            ],
        })

        expect(wrapper.get(".failure-debug-panel__subtitle").text()).toContain("task-1")
        expect(wrapper.find(".failure-switcher").exists()).toBe(true)
    })

    it("should fetch the flow once per execution, not again on every switcher focus change", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            flowRevision: 3,
            taskRunList: [
                taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z"),
                taskRun("tr-2", "task-2", "FAILED", "2024-01-01T00:00:10Z"),
            ],
        })
        await flushPromises()

        expect(mocks.flow).toHaveBeenCalledTimes(1)
        expect(mocks.flow).toHaveBeenCalledWith(expect.objectContaining({revision: 3}))

        await wrapper.get(".failure-switcher [role=\"tab\"]:last-child").trigger("click")
        await flushPromises()

        expect(mocks.flow).toHaveBeenCalledTimes(1)
    })

    it("should order the mini-timeline chronologically, matching the Gantt view, instead of focused-first by proximity", () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [
                taskRun("tr-1", "extract_orders", "SUCCESS", "2024-01-01T00:00:00Z"),
                taskRun("tr-2", "transform_orders", "SUCCESS", "2024-01-01T00:00:05Z"),
                taskRun("tr-3", "load_warehouse", "FAILED", "2024-01-01T00:00:10Z"),
            ],
        })

        const nodes = wrapper.findComponent(FailureMiniTimeline).props("nodes") as Array<{taskRun: {taskId: string}}>
        expect(nodes.map((node) => node.taskRun.taskId)).toEqual(["extract_orders", "transform_orders", "load_warehouse"])
    })

    it("should not render a failure switcher for a single failure", () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z")],
        })

        expect(wrapper.find(".failure-switcher").exists()).toBe(false)
    })

    it("should start closed, with the reopen affordance as the only entry point", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z")],
        })

        await flushPromises()

        expect(wrapper.get(".failure-debug-panel").isVisible()).toBe(false)
        expect(wrapper.get(".failure-debug-reopen button").isVisible()).toBe(true)
        expect(document.activeElement).not.toBe(wrapper.get("h3").element)
    })

    it("should move focus to the panel heading when opened", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z")],
        })
        await flushPromises()

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.get(".failure-debug-panel").isVisible()).toBe(true)
        expect(document.activeElement).toBe(wrapper.get("h3").element)
    })

    it("should close on Escape and return focus to the reopen affordance", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z")],
        })
        await flushPromises()
        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        await wrapper.get(".failure-debug-panel").trigger("keydown", {key: "Escape"})
        await flushPromises()

        expect(wrapper.get(".failure-debug-panel").isVisible()).toBe(false)
        expect(document.activeElement).toBe(wrapper.get(".failure-debug-reopen button").element)
    })

    it("should reopen and move focus back to the heading", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z")],
        })
        await flushPromises()
        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()
        await wrapper.get(".failure-debug-panel").trigger("keydown", {key: "Escape"})
        await flushPromises()

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.get(".failure-debug-panel").isVisible()).toBe(true)
        expect(document.activeElement).toBe(wrapper.get("h3").element)
    })

    it("should announce the failure as soon as it's detected, regardless of open state", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z")],
        })
        await flushPromises()

        const announcerBefore = wrapper.get("[role=\"status\"]").text()
        expect(announcerBefore).not.toBe("")
        expect(wrapper.get(".failure-debug-panel").isVisible()).toBe(false)

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()
        await wrapper.get(".failure-debug-panel").trigger("keydown", {key: "Escape"})
        await flushPromises()

        expect(wrapper.get("[role=\"status\"]").text()).toBe(announcerBefore)
    })
})
