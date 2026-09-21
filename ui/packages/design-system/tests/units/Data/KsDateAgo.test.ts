import {describe, test, expect, beforeAll, afterAll, vi} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsDateAgo from "../../../src/components/Data/KsDateAgo.vue"
import {DATE_FORMAT_STORAGE_KEY, TIMEZONE_STORAGE_KEY} from "../../../src/utils/date"

const globalConfig = {plugins: [KestraDesignSystem]}

const FIXED_DATE = "2024-01-15T10:00:00.000Z"
const FROM_NOW = "2 hours ago"
const FULL_DATE = "2024-01-15 10:00"

beforeAll(() => {
    localStorage.setItem(TIMEZONE_STORAGE_KEY, "UTC")
    localStorage.setItem(DATE_FORMAT_STORAGE_KEY, "YYYY-MM-DD HH:mm")
    vi.useFakeTimers()
    vi.setSystemTime(new Date("2024-01-15T12:00:00.000Z"))
})

afterAll(() => {
    vi.useRealTimers()
    localStorage.removeItem(TIMEZONE_STORAGE_KEY)
    localStorage.removeItem(DATE_FORMAT_STORAGE_KEY)
})

describe("KsDateAgo", () => {
    test("renders span element when date is provided", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE},
            global: globalConfig,
        })
        expect(wrapper.find("span").exists()).toBe(true)
    })

    test("renders nothing when date is not provided", () => {
        const wrapper = mount(KsDateAgo, {
            props: {},
            global: globalConfig,
        })
        expect(wrapper.find("span").exists()).toBe(false)
    })

    test("shows relative time by default", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE, showTooltip: false},
            global: globalConfig,
        })
        expect(wrapper.find("span").text()).toBe(FROM_NOW)
    })

    test("shows full date when inverted is true", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE, inverted: true, showTooltip: false},
            global: globalConfig,
        })
        expect(wrapper.find("span").text()).toBe(FULL_DATE)
    })

    test("formats the full date with the format prop", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE, inverted: true, showTooltip: false, format: "YYYY"},
            global: globalConfig,
        })
        expect(wrapper.find("span").text()).toBe("2024")
    })

    test("shows tooltip with full date when not inverted", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE, showTooltip: true},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(true)
        expect(wrapper.findComponent({name: "KsTooltip"}).props("content")).toBe(FULL_DATE)
    })

    test("shows tooltip with relative time when inverted", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE, inverted: true, showTooltip: true},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(true)
        expect(wrapper.findComponent({name: "KsTooltip"}).props("content")).toBe(FROM_NOW)
    })

    test("does not show tooltip when showTooltip is false", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE, showTooltip: false},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(false)
    })

    test("applies className to span", () => {
        const wrapper = mount(KsDateAgo, {
            props: {date: FIXED_DATE, showTooltip: false, className: "my-class"},
            global: globalConfig,
        })
        expect(wrapper.find("span").classes()).toContain("my-class")
    })
})
