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
})