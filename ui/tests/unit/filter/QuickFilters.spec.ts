import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import QuickFilters from "../../../src/components/filter/QuickFilters.vue"

const KsSegmentedStub = {
    name: "KsSegmented",
    props: ["options", "modelValue"],
    emits: ["change", "update:modelValue"],
    template: "<div class=\"ks-segmented-stub\"></div>",
}

const mountFilters = (props = {}) =>
    mount(QuickFilters, {
        props: {levels: [], intervals: [], ...props},
        global: {stubs: {KsSegmented: KsSegmentedStub}},
    })

const intervalSegment = (wrapper: ReturnType<typeof mountFilters>) =>
    wrapper
        .findAllComponents(KsSegmentedStub)
        .find((segment) => segment.attributes("data-test") === "quick-filters-interval")

const levelPills = (wrapper: ReturnType<typeof mountFilters>) =>
    wrapper.findAll("[data-test^=\"quick-filters-level-\"]")

describe("QuickFilters", () => {
    const LEVELS = [
        {label: "INFO", value: "INFO"},
        {label: "ERROR", value: "ERROR"},
    ]

    it("renders one level pill per provided level", () => {
        const wrapper = mountFilters({levels: LEVELS})

        const pills = levelPills(wrapper)
        expect(pills).toHaveLength(2)
        expect(pills.map((pill) => pill.text())).toEqual(["INFO", "ERROR"])
    })

    it("marks the active level pill", () => {
        const wrapper = mountFilters({levels: LEVELS, level: "ERROR"})

        expect(
            wrapper.find("[data-test=\"quick-filters-level-ERROR\"]").attributes("aria-pressed"),
        ).toBe("true")
        expect(
            wrapper.find("[data-test=\"quick-filters-level-INFO\"]").attributes("aria-pressed"),
        ).toBe("false")
    })

    it("emits update:level when a level pill is clicked", async () => {
        const wrapper = mountFilters({levels: LEVELS, level: "INFO"})

        await wrapper.find("[data-test=\"quick-filters-level-ERROR\"]").trigger("click")

        expect(wrapper.emitted("update:level")).toEqual([["ERROR"]])
    })

    it("renders an interval segmented control reflecting the active time range", () => {
        const intervals = [
            {label: "Last 1 hour", value: "PT1H"},
            {label: "Last 24 hours", value: "PT24H"},
        ]

        const wrapper = mountFilters({intervals, timeRange: "PT24H"})

        const segment = intervalSegment(wrapper)
        expect(segment).toBeTruthy()
        expect(segment!.props("options")).toEqual(intervals)
        expect(segment!.props("modelValue")).toBe("PT24H")
    })

    it("emits update:timeRange when an interval is selected", async () => {
        const wrapper = mountFilters({
            intervals: [
                {label: "Last 1 hour", value: "PT1H"},
                {label: "Last 24 hours", value: "PT24H"},
            ],
            timeRange: "PT1H",
        })

        const segment = intervalSegment(wrapper)
        segment!.vm.$emit("change", "PT24H")
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted("update:timeRange")).toEqual([["PT24H"]])
    })

    it("hides the interval control when showInterval is false but keeps the level pills", () => {
        const wrapper = mountFilters({
            levels: LEVELS,
            intervals: [{label: "Last 1 hour", value: "PT1H"}],
            showInterval: false,
        })

        expect(intervalSegment(wrapper)).toBeUndefined()
        expect(levelPills(wrapper)).toHaveLength(2)
    })

    it("hides the level pills when showLevel is false but keeps the interval", () => {
        const wrapper = mountFilters({
            levels: LEVELS,
            intervals: [{label: "Last 1 hour", value: "PT1H"}],
            showLevel: false,
        })

        expect(levelPills(wrapper)).toHaveLength(0)
        expect(intervalSegment(wrapper)).toBeTruthy()
    })
})
