import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import ChartLegend from "./ChartLegend.vue"

const i18n = createI18n({legacy: false, globalInjection: true, locale: "en", messages: {en: {}}})

const items = [
    {label: "SUCCESS", color: "var(--ks-chart-success)", count: 5},
    {label: "FAILED", color: "var(--ks-chart-failed)", count: 2},
]

function mountLegend(props: Record<string, unknown> = {}) {
    return mount(ChartLegend, {
        props: {items, ...props},
        global: {plugins: [i18n]},
    })
}

describe("ChartLegend", () => {
    it("should toggle a pill's own dimmed state locally when no toggledOff prop is given", async () => {
        const wrapper = mountLegend()

        const failedItem = wrapper.findAll(".item")[1]
        expect(failedItem.classes()).not.toContain("off")

        await failedItem.trigger("click")

        expect(wrapper.findAll(".item")[1].classes()).toContain("off")
    })

    it("should reflect an externally-controlled toggledOff set instead of its own local state", async () => {
        const wrapper = mountLegend({toggledOff: new Set(["FAILED"])})

        expect(wrapper.findAll(".item")[1].classes()).toContain("off")

        await wrapper.setProps({toggledOff: new Set()})

        expect(wrapper.findAll(".item")[1].classes()).not.toContain("off")
    })

    it("should still emit toggle in controlled mode without flipping its own local state", async () => {
        const wrapper = mountLegend({toggledOff: new Set()})

        await wrapper.findAll(".item")[1].trigger("click")

        expect(wrapper.emitted("toggle")).toEqual([["FAILED"]])
        expect(wrapper.findAll(".item")[1].classes()).not.toContain("off")
    })
})
