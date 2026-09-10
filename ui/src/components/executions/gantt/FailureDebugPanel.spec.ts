import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import type {Execution} from "../../../stores/executions"

const store = vi.hoisted(() => ({
    executions: {} as Record<string, unknown>,
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

import FailureDebugPanel from "./FailureDebugPanel.vue"
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
        global: {
            plugins: [i18n],
            stubs: {
                Restart: true,
                FailureMiniTimeline: true,
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
    // FailureLogPanel's `LogLine` import chain seeds `useLogDisplay` settings into localStorage
    // as a module-load side effect, even though FailureLogPanel is stubbed for these tests.
    afterAll(() => {
        localStorage.clear()
    })

    beforeEach(() => {
        store.executions = {loadLogs: vi.fn().mockResolvedValue([])}
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
        // Only the taskrun's latest (current) state matters, not that an earlier attempt failed.
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
        // The switcher only renders when more than one failure is present.
        expect(wrapper.find("[role=\"tablist\"]").exists()).toBe(true)
    })

    it("should not render a failure switcher for a single failure", () => {
        const wrapper = mountPanel({
            id: "exec-1",
            state: {current: "FAILED"},
            taskRunList: [taskRun("tr-1", "task-1", "FAILED", "2024-01-01T00:00:00Z")],
        })

        expect(wrapper.find("[role=\"tablist\"]").exists()).toBe(false)
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
