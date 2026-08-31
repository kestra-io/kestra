import {describe, it, expect, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const loadFlowForExecution = vi.fn().mockResolvedValue(undefined)

vi.mock("../../../../src/stores/api", () => ({
    useApiStore: () => ({posthogEvents: vi.fn()}),
}))

vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({flow: undefined, loadFlowForExecution, loadNamespaces: vi.fn(), flowsExecutable: [], namespaces: []}),
}))

vi.mock("../../../../src/stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
}))

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({executeFlow: false}),
}))

const toastSuccess = vi.fn()
vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({success: toastSuccess, error: vi.fn(), confirm: vi.fn()}),
}))

import TriggerFlow from "../../../../src/components/flows/TriggerFlow.vue"
import FlowRun from "../../../../src/components/flows/FlowRun.vue"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

function mountTriggerFlow(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
    return shallowMount(TriggerFlow, {
        props: {flowId: "my_flow", namespace: "company.team", ...props},
        slots,
        global: {plugins: [i18n]},
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
