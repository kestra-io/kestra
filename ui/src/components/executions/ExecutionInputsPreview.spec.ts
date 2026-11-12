import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {markRaw} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

import ExecutionInputsPreview from "./ExecutionInputsPreview.vue"

const loadFlowForExecution = vi.fn()
vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({loadFlowForExecution}),
}))

const variableValueRenderer = vi.fn()
vi.mock("../../override/components/executions/outputs/variableValueExtension", () => ({
    useVariableValueRenderer: () => variableValueRenderer,
}))

const AssetViewer = markRaw({props: ["value"], template: "<span data-test=\"asset-viewer\">{{ value }}</span>"})

function mount(flowId: string, inputs: Record<string, unknown>) {
    return i18nMount(ExecutionInputsPreview, {props: {namespace: "infra", flowId, flowRevision: 1, inputs}})
}

describe("ExecutionInputsPreview", () => {
    beforeEach(() => {
        loadFlowForExecution.mockReset()
        variableValueRenderer.mockReset()
    })

    it("renders an input through the viewer the edition gives for it, and the others as JSON", async () => {
        const flow = {inputs: [{id: "vm", type: "ASSET"}, {id: "count", type: "INT"}]}
        loadFlowForExecution.mockResolvedValue(flow)
        variableValueRenderer.mockImplementation((path: string, loaded: unknown) => loaded !== undefined && path === "inputs.vm" ? AssetViewer : undefined)

        const wrapper = mount("provision", {vm: "web-01", count: 2})
        await flushPromises()

        expect(wrapper.find("[data-test=asset-viewer]").text()).toBe("web-01")
        expect(wrapper.find("[data-test=execution-inputs-json]").text()).toBe("2")
    })

    it("shows the inputs as JSON when no input has a viewer of its own", async () => {
        loadFlowForExecution.mockResolvedValue({inputs: [{id: "count", type: "INT"}]})
        variableValueRenderer.mockReturnValue(undefined)

        const wrapper = mount("count-only", {count: 2})
        await flushPromises()

        expect(JSON.parse(wrapper.find("[data-test=execution-inputs-json]").text())).toEqual({count: 2})
    })

    it("loads the flow of a revision once for every execution hovered", async () => {
        loadFlowForExecution.mockResolvedValue({inputs: []})
        variableValueRenderer.mockReturnValue(undefined)

        mount("shared", {count: 1})
        mount("shared", {count: 2})
        await flushPromises()

        expect(loadFlowForExecution).toHaveBeenCalledTimes(1)
    })
})
