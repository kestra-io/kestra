import {describe, it, expect, vi} from "vitest"
const loadFlowForExecution = vi.fn().mockResolvedValue(undefined)

vi.mock("../../stores/api", () => ({
    useApiStore: () => ({posthogEvents: vi.fn()}),
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({flow: undefined, loadFlowForExecution, loadNamespaces: vi.fn(), flowsExecutable: [], namespaces: []}),
}))

vi.mock("../../stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
}))

const flowState: {executeFlow: boolean, flow?: {draft: boolean, revision: number}} = {executeFlow: false}
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowState,
}))

const settle = vi.fn().mockResolvedValue(undefined)
vi.mock("../../stores/flowAutosave", () => ({
    useFlowAutosaveStore: () => ({settle}),
}))

const toastSuccess = vi.fn()
vi.mock("../../utils/toast", () => ({
    useToast: () => ({success: toastSuccess, error: vi.fn(), confirm: vi.fn()}),
}))

import TriggerFlow from "./TriggerFlow.vue"
import FlowRun from "./FlowRun.vue"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"

function mountTriggerFlow(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
    return i18nShallowMount(TriggerFlow, {
        props: {flowId: "my_flow", namespace: "company.team", ...props},
        slots,
        // The real dialog keeps its content unmounted until opened, hiding the FlowRun these
        // tests reach for, so it renders its default slot instead.
        global: {stubs: {KsDialog: {template: "<div><slot /></div>"}}},
    })
}

describe("TriggerFlow — submit override", () => {
    it("forwards the submit prop to FlowRun as replaySubmit", () => {
        const submit = vi.fn()
        const wrapper = mountTriggerFlow({submit})

        expect(wrapper.findComponent(FlowRun).props("replaySubmit")).toBe(submit)
    })

    it("defaults to the normal execute path (replaySubmit unset) when no submit override is given", () => {
        const wrapper = mountTriggerFlow()

        expect(wrapper.findComponent(FlowRun).props("replaySubmit")).toBeFalsy()
    })
})

describe("TriggerFlow — lazy loading", () => {
    it("does not load the flow on mount when lazy", () => {
        loadFlowForExecution.mockClear()
        mountTriggerFlow({lazy: true})

        expect(loadFlowForExecution).not.toHaveBeenCalled()
    })

    it("loads the flow on click when lazy", async () => {
        loadFlowForExecution.mockClear()
        const wrapper = mountTriggerFlow({lazy: true})

        await wrapper.find("#execute-button").trigger("click")

        expect(loadFlowForExecution).toHaveBeenCalledWith({flowId: "my_flow", namespace: "company.team", store: true})
    })

    it("loads the flow on mount when not lazy (default, unchanged behavior)", () => {
        loadFlowForExecution.mockClear()
        mountTriggerFlow()

        expect(loadFlowForExecution).toHaveBeenCalledWith({flowId: "my_flow", namespace: "company.team", store: true})
    })
})

describe("TriggerFlow — #button slot", () => {
    it("renders the slot content instead of the default button, and its execute opens the dialog", async () => {
        loadFlowForExecution.mockClear()
        const wrapper = mountTriggerFlow({lazy: true}, {
            button: "<template #button=\"{execute}\"><button class=\"custom-trigger\" @click=\"execute()\">Run it</button></template>",
        })

        expect(wrapper.find("#execute-button").exists()).toBe(false)
        const custom = wrapper.find(".custom-trigger")
        expect(custom.exists()).toBe(true)

        await custom.trigger("click")

        expect(loadFlowForExecution).toHaveBeenCalledWith({flowId: "my_flow", namespace: "company.team", store: true})
    })
})

describe("TriggerFlow — execution-started feedback", () => {
    it("toasts on its own when it owns the submission", async () => {
        toastSuccess.mockClear()
        const wrapper = mountTriggerFlow()

        wrapper.findComponent(FlowRun).vm.$emit("executionTrigger")
        await wrapper.vm.$nextTick()

        expect(toastSuccess).toHaveBeenCalledTimes(1)
    })

    it("stays silent when a submit override owns the feedback, so the caller's toast is not duplicated", async () => {
        toastSuccess.mockClear()
        const wrapper = mountTriggerFlow({submit: vi.fn()})

        wrapper.findComponent(FlowRun).vm.$emit("executionTrigger")
        await wrapper.vm.$nextTick()

        expect(toastSuccess).not.toHaveBeenCalled()
    })
})

describe("TriggerFlow — test run", () => {
    it("runs the draft on screen as a playground execution, once autosave has pushed it", async () => {
        flowState.flow = {draft: true, revision: 4}
        loadFlowForExecution.mockClear()
        settle.mockClear()
        const wrapper = mountTriggerFlow({testRun: true, lazy: true})

        await wrapper.find("[data-test=flow-test-run]").trigger("click")

        expect(settle).toHaveBeenCalledOnce()
        expect(loadFlowForExecution).toHaveBeenCalledWith({flowId: "my_flow", namespace: "company.team", revision: 4, store: true})
        expect(wrapper.findComponent(FlowRun).props("testRun")).toBe(true)
        expect(wrapper.find("[data-test=test-run-description]").exists()).toBe(true)
        flowState.flow = undefined
    })

    it("offers no published run for a flow that was never published", () => {
        flowState.flow = {draft: true, revision: 1}
        const wrapper = mountTriggerFlow({testRun: true, lazy: true})

        expect(wrapper.find("[data-test=flow-test-run]").exists()).toBe(false)
        expect(wrapper.find("#execute-button").exists()).toBe(true)
        flowState.flow = undefined
    })
})
