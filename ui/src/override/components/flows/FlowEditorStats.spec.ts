import {describe, it, expect, beforeEach, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import FlowEditorStats from "./FlowEditorStats.vue"
import {useFlowStore} from "../../../stores/flow"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const stubs = {
    ValidationError: {
        name: "ValidationError",
        props: ["errors", "warnings", "infos", "iconOnly", "tooltipPlacement"],
        template: "<span />",
    },
    FlowEditorStatCounter: {
        name: "FlowEditorStatCounter",
        props: ["icon", "count", "suffix", "tooltip", "tab"],
        template: "<span />",
    },
}

function mountStats() {
    const wrapper = i18nMount(FlowEditorStats, {global: {stubs}})
    return {wrapper, validation: () => wrapper.findComponent({name: "ValidationError"})}
}

describe("FlowEditorStats.vue", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        const flowStore = useFlowStore()
        vi.spyOn(flowStore, "loadFlowStats").mockResolvedValue([
            {status: "fulfilled", value: []},
            {status: "fulfilled", value: {count: 0}},
        ])
        flowStore.flow = {id: "test", namespace: "test"} as never
        flowStore.flowValidation = {warnings: []} as never
        flowStore.ksEditorWarnings = []
    })

    it("passes no warnings when the editor and the backend have none", () => {
        const {validation} = mountStats()

        expect(validation().exists()).toBe(true)
        expect(validation().props("warnings")).toBeUndefined()
    })

    it("passes the editor warnings when the backend has none", () => {
        const flowStore = useFlowStore()
        flowStore.ksEditorWarnings = ["Line 15: Missing property 'taskRunner'"]

        const {validation} = mountStats()

        expect(validation().props("warnings")).toEqual(["Line 15: Missing property 'taskRunner'"])
    })

    it("merges the backend warnings with the editor warnings, backend first", () => {
        const flowStore = useFlowStore()
        flowStore.flowValidation = {warnings: ["Backend warning"]} as never
        flowStore.ksEditorWarnings = ["Line 15: Missing property 'taskRunner'"]

        const {validation} = mountStats()

        expect(validation().props("warnings")).toEqual([
            "Backend warning",
            "Line 15: Missing property 'taskRunner'",
        ])
    })

    it("follows the store when the editor warnings change or are cleared", async () => {
        const flowStore = useFlowStore()
        const {wrapper, validation} = mountStats()
        expect(validation().props("warnings")).toBeUndefined()

        flowStore.ksEditorWarnings = ["Line 1: first"]
        await wrapper.vm.$nextTick()
        expect(validation().props("warnings")).toEqual(["Line 1: first"])

        flowStore.ksEditorWarnings = []
        await wrapper.vm.$nextTick()
        expect(validation().props("warnings")).toBeUndefined()
    })

    it("shows no validation indicator while there is no flow", () => {
        const flowStore = useFlowStore()
        flowStore.flow = undefined as never
        flowStore.ksEditorWarnings = ["Line 1: first"]

        const {validation} = mountStats()

        expect(validation().exists()).toBe(false)
    })
})