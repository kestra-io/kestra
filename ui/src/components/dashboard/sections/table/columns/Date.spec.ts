import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import moment from "moment"
import DateColumn from "./Date.vue"

vi.mock("@kestra-io/design-system", () => ({
    KsTooltip: defineComponent({
        name: "KsTooltip",
        props: {content: {type: String, default: undefined}},
        template: "<span data-test=\"tooltip\" :data-content=\"content\"><slot /></span>",
    }),
}))

const FIELD = "2026-07-24T13:16:00.000Z"
const absolute = () => moment(FIELD).format("llll")

describe("dashboard table Date column", () => {
    it("should render the absolute date with a tooltip carrying the full value", () => {
        const wrapper = mount(DateColumn, {props: {field: FIELD}})

        const tooltip = wrapper.find("[data-test=tooltip]")
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.attributes("data-content")).toBe(absolute())
        expect(wrapper.find("span.date").text()).toBe(absolute())
    })

    it("should render the relative date with the absolute value in the tooltip", () => {
        const wrapper = mount(DateColumn, {props: {field: FIELD, relative: true}})

        const tooltip = wrapper.find("[data-test=tooltip]")
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.attributes("data-content")).toBe(absolute())
        expect(wrapper.find("span.date").text()).toBe(moment(FIELD).calendar(null, {sameElse: "L [at] LT"}))
    })

    it("should render nothing when there is no field", () => {
        const wrapper = mount(DateColumn, {props: {}})

        expect(wrapper.find("[data-test=tooltip]").exists()).toBe(false)
        expect(wrapper.text()).toBe("")
    })
})
