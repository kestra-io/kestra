import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTopologyDetails from "../../../src/components/Data/KsTopologyDetails.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const rows = [
    {label: "Provider", value: "Open AI - gpt-5-nano"},
    {label: "Memory", value: "JOHN"},
    {label: "Tool", value: "DockerMcpClient"},
]

describe("KsTopologyDetails", () => {
    test("renders one row per entry", () => {
        const wrapper = mount(KsTopologyDetails, {
            props: {rows},
            global: globalConfig,
        })
        expect(wrapper.findAll(".ks-topology-details__row")).toHaveLength(3)
    })

    test("renders label and value", () => {
        const wrapper = mount(KsTopologyDetails, {
            props: {rows},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-topology-details__label").text()).toBe("Provider")
        expect(wrapper.find(".ks-topology-details__value").text()).toBe("Open AI - gpt-5-nano")
    })

    test("renders nothing when rows is empty", () => {
        const wrapper = mount(KsTopologyDetails, {
            props: {rows: []},
            global: globalConfig,
        })
        expect(wrapper.findAll(".ks-topology-details__row")).toHaveLength(0)
    })
})
