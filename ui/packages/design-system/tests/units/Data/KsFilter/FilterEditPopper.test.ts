import {describe, test, expect} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import FilterEditPopper from "../../../../src/components/Data/KsDataTable/filter/layout/FilterEditPopper.vue"
import FilterSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterSelect.vue"
import FilterMultiSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterMultiSelect.vue"
import FilterDateTime from "../../../../src/components/Data/KsDataTable/filter/layout/FilterDateTime.vue"
import FilterComparatorSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterComparatorSelect.vue"
import FilterFooter from "../../../../src/components/Data/KsDataTable/filter/layout/FilterFooter.vue"
import {Comparators, type AppliedFilter, type FilterKeyConfig} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

const levelKey: FilterKeyConfig = {
    key: "level",
    label: "Level",
    valueType: "multi-select",
    comparators: [
        Comparators.GREATER_THAN_OR_EQUAL_TO,
        Comparators.LESS_THAN_OR_EQUAL_TO,
        Comparators.IN,
        Comparators.NOT_IN,
    ],
    comparatorLabels: {
        [Comparators.GREATER_THAN_OR_EQUAL_TO]: "At or Above",
        [Comparators.LESS_THAN_OR_EQUAL_TO]: "At or Below",
    },
    valueProvider: async () => [
        {label: "WARN", value: "WARN"},
        {label: "ERROR", value: "ERROR"},
    ],
}

const labelsKey: FilterKeyConfig = {
    key: "labels",
    label: "Labels",
    valueType: "key-value",
    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.IN, Comparators.NOT_IN],
}

const mountPopper = async (filter: AppliedFilter) => {
    const wrapper = mount(FilterEditPopper, {
        props: {filter, filterKey: levelKey, showComparatorSelection: true},
        global: globalConfig,
    })
    await flushPromises()
    return wrapper
}

describe("FilterEditPopper range comparators on a multi-select field", () => {
    test("renders a plain select (not the multi-select checkbox popover) for GREATER_THAN_OR_EQUAL_TO", async () => {
        const wrapper = await mountPopper({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            comparatorLabel: "At or Above",
            value: "WARN",
            valueLabel: "WARN",
        })

        expect(wrapper.findComponent(FilterSelect).exists()).toBe(true)
        expect(wrapper.findComponent(FilterMultiSelect).exists()).toBe(false)
    })

    test("renders the multi-select checkbox popover for IN", async () => {
        const wrapper = await mountPopper({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: ["WARN", "ERROR"],
            valueLabel: "WARN, ERROR",
        })

        expect(wrapper.findComponent(FilterMultiSelect).exists()).toBe(true)
        expect(wrapper.findComponent(FilterSelect).exists()).toBe(false)
    })

    test("switches from the multi-select popover to a plain select when the comparator changes to GREATER_THAN_OR_EQUAL_TO", async () => {
        const wrapper = await mountPopper({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: ["WARN", "ERROR"],
            valueLabel: "WARN, ERROR",
        })

        expect(wrapper.findComponent(FilterMultiSelect).exists()).toBe(true)

        wrapper.findComponent(FilterComparatorSelect).vm.$emit("update:selectedComparator", Comparators.GREATER_THAN_OR_EQUAL_TO)
        await flushPromises()

        expect(wrapper.findComponent(FilterMultiSelect).exists()).toBe(false)
        expect(wrapper.findComponent(FilterSelect).exists()).toBe(true)

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeTruthy()
        expect(updates!.at(-1)![0]).toMatchObject({
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            value: "",
        })
    })
})

describe("FilterEditPopper time range custom mode", () => {
    const timeRangeKey: FilterKeyConfig = {
        key: "timeRange",
        label: "Time range",
        valueType: "select",
        comparators: [Comparators.EQUALS],
        valueProvider: async () => [{label: "Last 24 hours", value: "PT24H"}],
    }

    const mountTimeRangePopper = async () => {
        const wrapper = mount(FilterEditPopper, {
            props: {
                filter: {
                    id: "f1",
                    key: "timeRange",
                    keyLabel: "Time range",
                    comparator: Comparators.EQUALS,
                    comparatorLabel: "Equals",
                    value: "PT24H",
                    valueLabel: "Last 24 hours",
                },
                filterKey: timeRangeKey,
            },
            global: globalConfig,
        })
        await flushPromises()
        return wrapper
    }

    test("applies a custom range as soon as only the Start Date is set, defaulting End Date to now", async () => {
        const wrapper = await mountTimeRangePopper()

        wrapper.findComponent(FilterSelect).vm.$emit("update:timeRangeMode", "custom")
        await flushPromises()

        const startDate = new Date("2026-06-01T00:00:00")
        const before = Date.now()
        wrapper.findComponent(FilterSelect).vm.$emit("update:startDateValue", startDate)
        await flushPromises()
        const after = Date.now()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeTruthy()
        const lastValue = updates!.at(-1)![0].value as {startDate: Date; endDate: Date}
        expect(lastValue.startDate).toEqual(startDate)
        expect(lastValue.endDate.getTime()).toBeGreaterThanOrEqual(before)
        expect(lastValue.endDate.getTime()).toBeLessThanOrEqual(after)
    })

    test("does not apply (and does not overwrite the previous filter) when switching to custom mode with neither bound set", async () => {
        const wrapper = await mountTimeRangePopper()

        wrapper.findComponent(FilterSelect).vm.$emit("update:timeRangeMode", "custom")
        await flushPromises()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeFalsy()
    })
})

describe("FilterEditPopper date field", () => {
    const expirationKey: FilterKeyConfig = {
        key: "expirationDate",
        label: "Expiration date",
        valueType: "date",
        comparators: [
            Comparators.GREATER_THAN_OR_EQUAL_TO,
            Comparators.LESS_THAN_OR_EQUAL_TO,
        ],
    }

    test("emits update when a date is picked (live apply)", async () => {
        const wrapper = mount(FilterEditPopper, {
            props: {
                filter: {
                    id: "f1",
                    key: "expirationDate",
                    keyLabel: "Expiration date",
                    comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
                    comparatorLabel: "At or After",
                    value: "",
                    valueLabel: "",
                },
                filterKey: expirationKey,
                showComparatorSelection: true,
            },
            global: globalConfig,
        })
        await flushPromises()

        const picked = new Date("2026-12-31T00:00:00")
        wrapper.findComponent(FilterDateTime).vm.$emit("update:dateValue", picked)
        await flushPromises()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeTruthy()
        expect(updates!.at(-1)![0]).toMatchObject({
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            value: picked,
        })
    })
})

describe("FilterEditPopper key-value comparator changes", () => {
    test("normalizes repeated keys before emitting a single-value comparator update", async () => {
        const wrapper = mount(FilterEditPopper, {
            props: {
                filter: {
                    id: "f1",
                    key: "labels",
                    keyLabel: "Labels",
                    comparator: Comparators.IN,
                    comparatorLabel: "In",
                    value: [
                        "invalid",
                        ":missing-key",
                        "empty-value:",
                        "environment:production",
                        "environment:staging",
                        "team:core",
                        "team:platform",
                    ],
                    valueLabel: "environment:production",
                },
                filterKey: labelsKey,
                showComparatorSelection: true,
            },
            global: globalConfig,
        })
        await flushPromises()

        wrapper.findComponent(FilterComparatorSelect).vm.$emit("update:selectedComparator", Comparators.EQUALS)
        await flushPromises()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toHaveLength(1)
        expect(updates![0][0]).toMatchObject({
            comparator: Comparators.EQUALS,
            value: ["environment:staging", "team:platform"],
            valueLabel: "environment:staging",
        })
    })
})

const timeRangeKey: FilterKeyConfig = {
    key: "timeRange",
    label: "Interval",
    valueType: "time-range",
    customDateMode: "range",
    defaultValue: "PT24H",
    comparators: [Comparators.EQUALS],
    valueProvider: async () => [
        {label: "Last 24 hours", value: "PT24H"},
        {label: "Last 7 days", value: "P7D"},
    ],
}

const mountTimeRange = async (filter: AppliedFilter) => {
    const wrapper = mount(FilterEditPopper, {
        props: {filter, filterKey: timeRangeKey},
        global: globalConfig,
    })
    await flushPromises()
    return wrapper
}

describe("FilterEditPopper time range", () => {
    test("restores the configured default on reset instead of clearing the value", async () => {
        const wrapper = await mountTimeRange({
            id: "tr1",
            key: "timeRange",
            comparator: Comparators.EQUALS,
            value: "P7D",
            valueLabel: "Last 7 days",
        } as AppliedFilter)

        wrapper.findComponent(FilterFooter).vm.$emit("reset")
        await flushPromises()

        const updates = wrapper.emitted("update") ?? []
        const last = updates.at(-1)
        expect(last).toBeDefined()
        expect((last![0] as AppliedFilter).value).toBe("PT24H")
    })

    test("does not apply a custom range whose start is after its end", async () => {
        const wrapper = await mountTimeRange({
            id: "tr2",
            key: "timeRange",
            comparator: Comparators.EQUALS,
            value: "PT24H",
            valueLabel: "Last 24 hours",
        } as AppliedFilter)

        const select = wrapper.findComponent(FilterSelect)
        select.vm.$emit("update:timeRangeMode", "custom")
        select.vm.$emit("update:startDateValue", new Date("2026-08-20T10:00:00.000Z"))
        select.vm.$emit("update:endDateValue", new Date("2026-08-10T10:00:00.000Z"))
        await flushPromises()

        // An inverted range is refused by the API with a 422, so it must never be applied.
        const applied = (wrapper.emitted("update") ?? [])
            .map(([filter]) => filter as AppliedFilter)
            .filter(filter => typeof filter.value === "object" && filter.value !== null)

        applied.forEach(filter => {
            const {startDate, endDate} = filter.value as {startDate: Date; endDate: Date}
            expect(startDate.getTime()).toBeLessThanOrEqual(endDate.getTime())
        })
    })
})
