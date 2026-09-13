import {describe, it, expect, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({flow: {id: "my_flow", namespace: "company.team"}}),
}))

import FlowExecutions from "../../../../src/components/flows/FlowExecutions.vue"
import Executions from "../../../../src/components/executions/Executions.vue"

function mountFlowExecutions(embed?: boolean) {
    return shallowMount(FlowExecutions, {
        props: embed === undefined ? {} : {embed},
    })
}

describe("FlowExecutions.vue — embed prop forwarding", () => {
    it("forwards embed:true to Executions so it doesn't clobber the flow's title", () => {
        const wrapper = mountFlowExecutions(true)
        expect(wrapper.findComponent(Executions).props("embed")).toBe(true)
    })

    it("forwards embed:false/undefined to Executions (standalone behavior unchanged)", () => {
        const wrapper = mountFlowExecutions()
        expect(wrapper.findComponent(Executions).props("embed")).toBeFalsy()
    })
})
