import {afterEach, describe, test, expect} from "vitest"
import FilterChip from "./FilterChip.vue"
import {Comparators, type AppliedFilter} from "../utils/filterTypes"
import {i18nMount} from "../../../../../../tests/units/i18nMount"
import {TIMEZONE_STORAGE_KEY} from "../../../../../utils/date"

const timeRangeChip = (value: AppliedFilter["value"]) => ({
    id: "f1",
    key: "timeRange",
    keyLabel: "Started",
    comparator: Comparators.EQUALS,
    comparatorLabel: "Equals",
    value,
    valueLabel: "",
} as AppliedFilter)

const mountChip = (value: AppliedFilter["value"]) =>
    i18nMount(FilterChip, {
        props: {filter: timeRangeChip(value)},
    })

describe("FilterChip relative date labels", () => {
    test.each([
        ["PT720H", "datepicker.last30days"],
        ["P30D", "datepicker.last30days"],
        ["PT168H", "datepicker.last7days"],
    ])("labels %s as %s rather than the raw duration", (value, label) => {
        const wrapper = mountChip(value)

        expect(wrapper.text()).toContain(label)
        expect(wrapper.text()).not.toContain(value)
    })

    test("falls back to the raw duration for a value with no matching option", () => {
        expect(mountChip("PT3H").text()).toContain("PT3H")
    })
})

describe("FilterChip absolute range timezone", () => {
    afterEach(() => {
        localStorage.removeItem(TIMEZONE_STORAGE_KEY)
    })

    test("renders an absolute Started range in the Settings timezone", () => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, "America/New_York")

        const wrapper = mountChip({
            startDate: new Date("2026-08-01T00:00:00.000Z"),
            endDate: new Date("2026-08-02T00:00:00.000Z"),
        })

        expect(wrapper.text()).toContain("Jul 31, 2026 8:00 PM")
        expect(wrapper.text()).toContain("Aug 1, 2026 8:00 PM")
    })
})

describe("FilterChip remove action", () => {
    const mountWithKey = (removable?: boolean) =>
        i18nMount(FilterChip, {
            props: {
                filter: timeRangeChip("P30D"),
                filterKey: {key: "timeRange", label: "Interval", valueType: "select", comparators: [Comparators.EQUALS], removable},
            },
        })

    test("offers no way to remove a non-removable chip", async () => {
        const wrapper = mountWithKey(false)

        await wrapper.trigger("auxclick", {button: 1})

        expect(wrapper.find("[data-test='filter-chip-remove']").exists()).toBe(false)
        expect(wrapper.emitted("remove")).toBeUndefined()
    })

    test("keeps the remove button on a regular chip", () => {
        expect(mountWithKey().find("[data-test='filter-chip-remove']").exists()).toBe(true)
    })
})
