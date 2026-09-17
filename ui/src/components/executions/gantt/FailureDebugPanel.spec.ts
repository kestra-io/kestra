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

function taskRun(id: string, taskId: string, state: string, startDate: string, extra: Record<string, unknown> = {}) {
    return {
        id,
        taskId,
        state: {current: state, histories: [history("RUNNING", startDate), history(state, startDate)]},
        ...extra,
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
                FailureAttempts: true,
                SubFlowLink: true,
                KsExecutionStatus: true,
                KsButton: {props: ["disabled"], template: "<button :disabled=\"disabled\"><slot /></button>"},
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

    it("should identify the failing iteration by its value, not just the task id", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [
                taskRun("tr-1", "process_item", "FAILED", "2024-01-01T00:00:00Z", {value: "customer-4711"}),
            ],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.get(".failure-debug-panel__value").text()).toBe("customer-4711")
    })

    it("should distinguish two failures of the same task id by their value in the switcher", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [
                taskRun("tr-1", "process_item", "FAILED", "2024-01-01T00:00:00Z", {value: "customer-1"}),
                taskRun("tr-2", "process_item", "FAILED", "2024-01-01T00:00:05Z", {value: "customer-2"}),
            ],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        const values = wrapper.findAll(".failure-switcher__value").map((node) => node.text())
        expect(values).toEqual(["customer-1", "customer-2"])
    })

    it("should show which attempt failed when the task run was retried", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [
                taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z", {
                    attempts: [{state: {current: "FAILED", histories: []}}, {state: {current: "FAILED", histories: []}}],
                }),
            ],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.get(".failure-debug-panel__attempt").text()).toBe("Attempt 2/2")
    })

    it("should not show an attempt counter for a task run with a single attempt", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [
                taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z", {
                    attempts: [{state: {current: "FAILED", histories: []}}],
                }),
            ],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.find(".failure-debug-panel__attempt").exists()).toBe(false)
    })

    it("should surface the error message on its own, without making the user read the log stream", async () => {
        store.executions = {
            loadLogs: vi.fn().mockResolvedValue({
                results: [
                    {level: "INFO", message: "starting"},
                    {level: "ERROR", message: "Connection refused: warehouse:5432"},
                ],
            }),
        }

        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z")],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.get(".failure-error-summary__text").text()).toBe("Connection refused: warehouse:5432")
    })

    it("should not fetch the error while the panel is closed, so a failed execution's Gantt view costs nothing extra", async () => {
        const loadLogs = vi.fn().mockResolvedValue({results: [{level: "ERROR", message: "boom"}]})
        store.executions = {loadLogs}

        mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z")],
        })
        await flushPromises()

        expect(loadLogs).not.toHaveBeenCalled()
    })

    it("should not refetch the error when the same failure is closed and reopened", async () => {
        const loadLogs = vi.fn().mockResolvedValue({results: [{level: "ERROR", message: "boom"}]})
        store.executions = {loadLogs}

        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z")],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()
        await wrapper.get(".failure-debug-panel").trigger("keydown", {key: "Escape"})
        await flushPromises()
        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(loadLogs).toHaveBeenCalledTimes(1)
    })

    it("should fetch the error once per focused failure rather than once per action that needs it", async () => {
        const loadLogs = vi.fn().mockResolvedValue({results: [{level: "ERROR", message: "boom"}]})
        store.executions = {loadLogs}

        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z")],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(loadLogs).toHaveBeenCalledTimes(1)
    })

    it("should disable the error-dependent actions when no error text was recorded", async () => {
        store.executions = {loadLogs: vi.fn().mockResolvedValue({results: []})}

        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z")],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        const actions = wrapper.findAll(".failure-debug-panel__actions-secondary button")
        const byLabel = (label: string) => actions.find((button) => button.text().trim() === label)

        expect(byLabel("Ask Copilot")?.attributes("disabled")).toBeDefined()
        expect(byLabel("Copy error")?.attributes("disabled")).toBeDefined()
        expect(byLabel("Edit flow")?.attributes("disabled")).toBeUndefined()
    })

    it("should keep the error-dependent actions enabled once an error was recorded", async () => {
        store.executions = {loadLogs: vi.fn().mockResolvedValue({results: [{level: "ERROR", message: "boom"}]})}

        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z")],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        const actions = wrapper.findAll(".failure-debug-panel__actions-secondary button")
        const byLabel = (label: string) => actions.find((button) => button.text().trim() === label)

        expect(byLabel("Ask Copilot")?.attributes("disabled")).toBeUndefined()
        expect(byLabel("Copy error")?.attributes("disabled")).toBeUndefined()
    })

    it("should offer a drill-down into the child execution when the failing task is a subflow", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [
                taskRun("tr-1", "run_child", "FAILED", "2024-01-01T00:00:00Z", {outputs: {executionId: "child-exec-9"}}),
            ],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.findComponent({name: "SubFlowLink"}).exists()).toBe(true)
    })

    it("should not offer a subflow drill-down for a task that produced no child execution", async () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "load_warehouse", "FAILED", "2024-01-01T00:00:00Z")],
        })

        await wrapper.get(".failure-debug-reopen button").trigger("click")
        await flushPromises()

        expect(wrapper.findComponent({name: "SubFlowLink"}).exists()).toBe(false)
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
