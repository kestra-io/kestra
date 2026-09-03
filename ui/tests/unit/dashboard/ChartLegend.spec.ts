import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import ChartLegend from "../../../src/components/dashboard/sections/ChartLegend.vue"

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
})
