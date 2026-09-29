import {describe, test, expect} from "vitest"
import FilterChip from "../../../../src/components/Data/KsDataTable/filter/layout/FilterChip.vue"
import {Comparators, type AppliedFilter} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import {i18nMount} from "../../i18nMount"

const timeRangeChip = (value: string) => ({
    id: "f1",
    key: "timeRange",
    comparator: Comparators.EQUALS,
    value,
} as AppliedFilter)

const mountChip = (value: string) =>
    i18nMount(FilterChip, {
        props: {filter: timeRangeChip(value)},
    })

describe("FilterChip relative date labels", () => {
    test.each([
        ["PT720H", "datepicker.last30days"],
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
