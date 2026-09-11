import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import moment from "moment-timezone"
import {storageKeys} from "../../../../../utils/constants"
import DateColumn from "./Date.vue"

vi.mock("@kestra-io/design-system", () => ({
    KsTooltip: defineComponent({
        name: "KsTooltip",
        props: {content: {type: String, default: undefined}},
        template: "<span data-test=\"tooltip\" :data-content=\"content\"><slot /></span>",
    }),
}))

const FIELD = "2026-07-24T13:16:00.000Z"
const TIMEZONE = "America/Los_Angeles"

const inTimezone = (format: string) => moment(FIELD).tz(TIMEZONE).format(format)

describe("dashboard table Date column", () => {
    beforeEach(() => localStorage.clear())
    afterEach(() => localStorage.clear())

    it("should render the absolute date with a tooltip carrying the full value", () => {
        const wrapper = mount(DateColumn, {props: {field: FIELD}})

        const expected = moment(FIELD).tz(moment.tz.guess()).format("llll")
        const tooltip = wrapper.find("[data-test=tooltip]")
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.attributes("data-content")).toBe(expected)
        expect(wrapper.find("span.date").text()).toBe(expected)
    })

    it("should render the relative date with the absolute value in the tooltip", () => {
        const wrapper = mount(DateColumn, {props: {field: FIELD, relative: true}})

        const tooltip = wrapper.find("[data-test=tooltip]")
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.attributes("data-content")).toBe(moment(FIELD).tz(moment.tz.guess()).format("llll"))
        expect(wrapper.find("span.date").text())
            .toBe(moment(FIELD).tz(moment.tz.guess()).calendar(null, {sameElse: "L [at] LT"}))
    })

    it("should render nothing when there is no field", () => {
        const wrapper = mount(DateColumn, {props: {}})

        expect(wrapper.find("[data-test=tooltip]").exists()).toBe(false)
        expect(wrapper.text()).toBe("")
    })

    it("should format the absolute date in the timezone from settings", () => {
        localStorage.setItem(storageKeys.TIMEZONE_STORAGE_KEY, TIMEZONE)

        const wrapper = mount(DateColumn, {props: {field: FIELD}})

        expect(wrapper.find("span.date").text()).toBe(inTimezone("llll"))
        expect(wrapper.find("[data-test=tooltip]").attributes("data-content")).toBe(inTimezone("llll"))
    })

    it("should format the relative date in the timezone from settings", () => {
        localStorage.setItem(storageKeys.TIMEZONE_STORAGE_KEY, TIMEZONE)

        const wrapper = mount(DateColumn, {props: {field: FIELD, relative: true}})

        expect(wrapper.find("span.date").text())
            .toBe(moment(FIELD).tz(TIMEZONE).calendar(null, {sameElse: "L [at] LT"}))
    })

    it("should honour the date format from settings alongside the timezone", () => {
        localStorage.setItem(storageKeys.TIMEZONE_STORAGE_KEY, TIMEZONE)
        localStorage.setItem(storageKeys.DATE_FORMAT_STORAGE_KEY, "YYYY-MM-DD HH:mm")

        const wrapper = mount(DateColumn, {props: {field: FIELD}})

        // 13:16 UTC is 06:16 in Los Angeles, so a wrong timezone shows a different hour.
        expect(wrapper.find("span.date").text()).toBe("2026-07-24 06:16")
    })
})
