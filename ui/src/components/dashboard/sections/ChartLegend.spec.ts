import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import ChartLegend from "./ChartLegend.vue"

const items = [
    {label: "SUCCESS", color: "var(--ks-chart-success)", count: 5},
    {label: "FAILED", color: "var(--ks-chart-failed)", count: 2},
]

function mountLegend(props: Record<string, unknown> = {}) {
    return mount(ChartLegend, {props: {items, ...props}})
}

describe("ChartLegend", () => {
    test("wraps each level count in parentheses", () => {
        const wrapper = mount(ChartLegend, {
            props: {
                items: [
                    {label: "INFO", color: "#5B9CFF", count: 1292},
                    {label: "ERROR", color: "#F05A5A", count: 61},
                ],
            },
        })

        expect(wrapper.text()).toContain("Info (1292)")
        expect(wrapper.text()).toContain("Error (61)")
    })

    test("renders duration aggregations through the provided formatter", () => {
        const wrapper = mount(ChartLegend, {
            props: {
                items: [
                    {label: "SUCCESS", color: "#028090", count: 6.3},
                    {label: "FAILED", color: "#F05A5A", count: 0.11},
                ],
                formatValue: (value: number) => `${value.toFixed(2)}s`,
            },
        })

        expect(wrapper.text()).toContain("Success (6.30s)")
        expect(wrapper.text()).toContain("Failed (0.11s)")
        expect(wrapper.text()).not.toContain("(6.3)")
    })

    test("should toggle a pill's own dimmed state locally when no toggledOff prop is given", async () => {
        const wrapper = mountLegend()

        const failedItem = wrapper.findAll(".item")[1]
        expect(failedItem.classes()).not.toContain("off")

        await failedItem.trigger("click")

        expect(wrapper.findAll(".item")[1].classes()).toContain("off")
    })

    test("should reflect an externally-controlled toggledOff set instead of its own local state", async () => {
        const wrapper = mountLegend({toggledOff: new Set(["FAILED"])})

        expect(wrapper.findAll(".item")[1].classes()).toContain("off")

        await wrapper.setProps({toggledOff: new Set()})

        expect(wrapper.findAll(".item")[1].classes()).not.toContain("off")
    })

    test("should still emit toggle in controlled mode without flipping its own local state", async () => {
        const wrapper = mountLegend({toggledOff: new Set()})

        await wrapper.findAll(".item")[1].trigger("click")

        expect(wrapper.emitted("toggle")).toEqual([["FAILED"]])
        expect(wrapper.findAll(".item")[1].classes()).not.toContain("off")
    })
})
