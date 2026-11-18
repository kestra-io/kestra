import {defineComponent, h} from "vue"
import {describe, expect, it} from "vitest"
import FlowSummaryChip from "./FlowSummaryChip.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const KsTooltipStub = defineComponent({
    name: "KsTooltip",
    props: {content: {type: String, default: undefined}, placement: {type: String, default: undefined}},
    setup(_props, {slots}) {
        return () => h("span", {}, slots.default?.())
    },
})

function mountFlowSummaryChip(props: Record<string, unknown> = {}) {
    return i18nMount(FlowSummaryChip, {
        props,
        global: {
            stubs: {
                KsTooltip: KsTooltipStub,
                KsTag: {template: "<span><slot /></span>"},
            },
        },
    })
}

function tooltipContent(wrapper: ReturnType<typeof mountFlowSummaryChip>): string {
    const tooltips = wrapper.findAllComponents(KsTooltipStub)
    const first = tooltips[0]
    expect(first).toBeDefined()
    return first!.props("content") as string
}

describe("FlowSummaryChip fullPath", () => {
    it("should render namespace / flowId when both are given", () => {
        const wrapper = mountFlowSummaryChip({namespace: "io.kestra", flowId: "my-flow"})

        expect(tooltipContent(wrapper)).toBe("io.kestra / my-flow")
        expect(wrapper.find(".flow-chip-namespace").text()).toBe("io.kestra")
        expect(wrapper.find(".flow-chip-id").text()).toBe("my-flow")
    })

    it("should use just the namespace in the tooltip when flowId is missing", () => {
        const wrapper = mountFlowSummaryChip({namespace: "io.kestra"})

        expect(tooltipContent(wrapper)).toBe("io.kestra")
        expect(wrapper.find(".flow-chip-namespace").text()).toBe("io.kestra")
        expect(wrapper.find(".flow-chip-id").text()).toBe("")
        expect(tooltipContent(wrapper)).not.toContain(" / ")
    })

    it("should use just the flowId in the tooltip when namespace is missing", () => {
        const wrapper = mountFlowSummaryChip({flowId: "my-flow"})

        expect(tooltipContent(wrapper)).toBe("my-flow")
        expect(wrapper.find(".flow-chip-namespace").text()).toBe("")
        expect(wrapper.find(".flow-chip-id").text()).toBe("my-flow")
        expect(tooltipContent(wrapper)).not.toContain(" / ")
    })

    it("should use an empty tooltip when both are missing", () => {
        const wrapper = mountFlowSummaryChip({})

        expect(tooltipContent(wrapper)).toBe("")
        expect(tooltipContent(wrapper)).not.toBe(" / ")
        expect(wrapper.find(".flow-chip-namespace").text()).toBe("")
        expect(wrapper.find(".flow-chip-id").text()).toBe("")
    })
})

describe("FlowSummaryChip hasDetails", () => {
    it("should be expandable when there is a description", () => {
        const wrapper = mountFlowSummaryChip({description: "a description"})

        expect(wrapper.find("[data-test=\"topology-flow-expand\"]").exists()).toBe(true)
    })

    it("should be expandable when there are labels", () => {
        const wrapper = mountFlowSummaryChip({labels: [["env", "prod"]]})

        expect(wrapper.find("[data-test=\"topology-flow-expand\"]").exists()).toBe(true)
    })

    it("should not be expandable when there is neither description nor labels", () => {
        const wrapper = mountFlowSummaryChip({})

        expect(wrapper.find("[data-test=\"topology-flow-expand\"]").exists()).toBe(false)
    })

    it("should not be expandable when description is empty and labels are empty", () => {
        const wrapper = mountFlowSummaryChip({description: "", labels: []})

        expect(wrapper.find("[data-test=\"topology-flow-expand\"]").exists()).toBe(false)
    })
})

describe("FlowSummaryChip expand control", () => {
    it("should reveal the description and the labels when toggled", async () => {
        const wrapper = mountFlowSummaryChip({
            description: "flow description",
            labels: [["env", "prod"], ["team", "backend"]],
        })

        expect(wrapper.find(".flow-chip-details").exists()).toBe(false)
        expect(wrapper.find(".flow-chip-description").exists()).toBe(false)

        await wrapper.find("[data-test=\"topology-flow-expand\"]").trigger("click")

        expect(wrapper.find(".flow-chip-details").exists()).toBe(true)
        expect(wrapper.find(".flow-chip-description").text()).toBe("flow description")
        expect(wrapper.find(".flow-chip-labels").exists()).toBe(true)
        expect(wrapper.find(".flow-chip-labels").text()).toContain("env: prod")
        expect(wrapper.find(".flow-chip-labels").text()).toContain("team: backend")
    })

    it("should collapse the details when toggled again", async () => {
        const wrapper = mountFlowSummaryChip({description: "flow description"})

        await wrapper.find("[data-test=\"topology-flow-expand\"]").trigger("click")
        expect(wrapper.find(".flow-chip-details").exists()).toBe(true)

        await wrapper.find("[data-test=\"topology-flow-expand\"]").trigger("click")
        expect(wrapper.find(".flow-chip-details").exists()).toBe(false)
    })
})

describe("FlowSummaryChip edit action", () => {
    it("should emit editFlow when the identity chip is clicked", async () => {
        const wrapper = mountFlowSummaryChip({flowId: "my-flow", namespace: "io.kestra"})

        await wrapper.find("[data-test=\"topology-flow-chip\"]").trigger("click")

        expect(wrapper.emitted("editFlow")).toHaveLength(1)
    })

    it("should emit editFlow when the configure action is clicked", async () => {
        const wrapper = mountFlowSummaryChip({flowId: "my-flow"})

        await wrapper.find("[data-test=\"topology-flow-configure\"]").trigger("click")

        expect(wrapper.emitted("editFlow")).toHaveLength(1)
    })
})
