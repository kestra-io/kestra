import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import RouterMd from "./RouterMd.vue"

const stubs = {
    RouterLink: {
        template: '<a :href="to?.name ?? \'\'" :data-to="JSON.stringify(to)"><slot /></a>',
        props: ["to"],
    },
}

const defaultProps = {
    execution: "exec-abc-123",
    flowId: "my-flow",
    namespace: "io.kestra.demo",
}

function mountComponent(props = defaultProps) {
    return mount(RouterMd, {
        props,
        global: {stubs, mocks: {$t: (key: string) => key}},
    })
}

describe("RouterMd", () => {
    test("shouldRenderExecutionLinkTargetingExecutionsUpdate", () => {
        const wrapper = mountComponent()
        const links = wrapper.findAll("a")

        const execLink = links[0]
        const to = JSON.parse(execLink.attributes("data-to")!)
        expect(to.name).toBe("executions/update")
        expect(to.params.id).toBe(defaultProps.execution)
        expect(to.params.namespace).toBe(defaultProps.namespace)
        expect(to.params.flowId).toBe(defaultProps.flowId)
    })

    test("shouldRenderExecutionLinkTextAsExecutionId", () => {
        const wrapper = mountComponent()
        const execLink = wrapper.findAll("a")[0]

        expect(execLink.text().trim()).toBe(defaultProps.execution)
    })

    test("shouldRenderFlowLinkTargetingFlowsUpdate", () => {
        const wrapper = mountComponent()
        const links = wrapper.findAll("a")

        const flowLink = links[1]
        const to = JSON.parse(flowLink.attributes("data-to")!)
        expect(to.name).toBe("flows/update")
        expect(to.params.namespace).toBe(defaultProps.namespace)
        expect(to.params.id).toBe(defaultProps.flowId)
    })

    test("shouldRenderFlowLinkTextAsNamespaceDotFlowId", () => {
        const wrapper = mountComponent()
        const flowLink = wrapper.findAll("a")[1]

        expect(flowLink.text().trim()).toBe(`${defaultProps.namespace}.${defaultProps.flowId}`)
    })

    test("shouldRenderTranslatedForFlowTextBetweenLinks", () => {
        const wrapper = mountComponent()
        const text = wrapper.text()
        const links = wrapper.findAll("a")

        const execEnd = text.indexOf(links[0].text().trim()) + links[0].text().trim().length
        const flowStart = text.indexOf(links[1].text().trim())
        const between = text.slice(execEnd, flowStart).trim()

        expect(between).toBe("for flow")
    })
})
