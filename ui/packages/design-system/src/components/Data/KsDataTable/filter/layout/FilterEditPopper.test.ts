import {describe, test, expect} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

import FilterEditPopper from "./FilterEditPopper.vue"
import FilterSelect from "./FilterSelect.vue"
import FilterMultiSelect from "./FilterMultiSelect.vue"
import FilterDateTime from "./FilterDateTime.vue"
import FilterNumber from "./FilterNumber.vue"
import FilterComparatorSelect from "./FilterComparatorSelect.vue"
import FilterFooter from "./FilterFooter.vue"
import {Comparators, type AppliedFilter, type FilterKeyConfig} from "../utils/filterTypes"
import filterLocale from "../../KsFilter.locale"

test.each([
    {allLabel: "Standard", expected: "Standard sélectionné"},
    {allLabel: undefined, expected: `${filterLocale.fr.filter.hierarchy.all} sélectionné`},
])("shouldTranslateTheDefaultRadioFooterWithLabel $allLabel", async ({allLabel, expected}) => {
    const wrapper = i18nMount(FilterEditPopper, {
        locales: filterLocale,
        props: {
            filter: {id: "kind", key: "kind", keyLabel: "Kind", comparator: Comparators.EQUALS, comparatorLabel: "", value: "", valueLabel: ""},
            filterKey: {
                key: "kind",
                label: "Kind",
                valueType: "radio",
                comparators: [Comparators.EQUALS],
                allLabel,
                valueProvider: async () => [{label: "Playground", value: "PLAYGROUND"}],
            },
        },
    })
    wrapper.vm.$i18n.locale = "fr"
    await flushPromises()

    expect(wrapper.text()).toContain(expected)
})

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
    const wrapper = i18nMount(FilterEditPopper, {
        props: {filter, filterKey: levelKey, showComparatorSelection: true},
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
        const wrapper = i18nMount(FilterEditPopper, {
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
        const wrapper = i18nMount(FilterEditPopper, {
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
        const wrapper = i18nMount(FilterEditPopper, {
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
    const wrapper = i18nMount(FilterEditPopper, {
        props: {filter, filterKey: timeRangeKey},
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

    test("does not apply a start date later than the end date it defaults to", async () => {
        const wrapper = await mountTimeRange({
            id: "tr3",
            key: "timeRange",
            comparator: Comparators.EQUALS,
            value: "PT24H",
            valueLabel: "Last 24 hours",
        } as AppliedFilter)

        const select = wrapper.findComponent(FilterSelect)
        select.vm.$emit("update:timeRangeMode", "custom")
        select.vm.$emit("update:startDateValue", new Date(Date.now() + 3 * 24 * 60 * 60 * 1000))
        await flushPromises()

        // With no end date the range still gets one, from getFilterValue's `?? new Date()`, so a
        // future start inverts it just as surely as an explicit end in the past.
        const applied = (wrapper.emitted("update") ?? [])
            .map(([filter]) => filter as AppliedFilter)
            .filter(filter => typeof filter.value === "object" && filter.value !== null)

        applied.forEach(filter => {
            const {startDate, endDate} = filter.value as {startDate: Date; endDate: Date}
            expect(startDate.getTime()).toBeLessThanOrEqual(endDate.getTime())
        })
    })
})

const attemptsKey: FilterKeyConfig = {
    key: "attempts",
    label: "Attempts",
    valueType: "number",
    comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.BETWEEN],
}

describe("FilterEditPopper number field", () => {
    const mountNumber = async (comparator: Comparators) => {
        const wrapper = i18nMount(FilterEditPopper, {
            props: {
                filter: {id: "f1", key: "attempts", keyLabel: "Attempts", comparator, comparatorLabel: "", value: "", valueLabel: ""},
                filterKey: attemptsKey,
                showComparatorSelection: true,
            },
        })
        await flushPromises()
        return wrapper
    }

    const lastUpdate = (wrapper: Awaited<ReturnType<typeof mountNumber>>) =>
        (wrapper.emitted("update") as Array<[AppliedFilter]> | undefined)?.at(-1)?.[0]

    const updates = (wrapper: Awaited<ReturnType<typeof mountNumber>>) =>
        (wrapper.emitted("update") as Array<[AppliedFilter]> | undefined) ?? []

    // Number edits go through the same 400ms debounce as text, so a test waits it out.
    const settle = async () => {
        await new Promise((resolve) => setTimeout(resolve, 450))
        await flushPromises()
    }

    test("shouldApplyOnlyTheLastNumberWhenTypingSeveralDigits", async () => {
        const wrapper = await mountNumber(Comparators.GREATER_THAN_OR_EQUAL_TO)
        const number = wrapper.findComponent(FilterNumber)

        number.vm.$emit("update:modelValue", "1")
        number.vm.$emit("update:modelValue", "12")
        number.vm.$emit("update:modelValue", "125")
        await settle()

        expect(updates(wrapper)).toHaveLength(1)
        expect(lastUpdate(wrapper)).toMatchObject({comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, value: "125", valueLabel: "125"})
    })

    test("shouldApplyBetweenOnlyOnceBothBoundsAreSetInOrder", async () => {
        const wrapper = await mountNumber(Comparators.BETWEEN)
        const number = wrapper.findComponent(FilterNumber)
        expect(number.props("range")).toBe(true)

        number.vm.$emit("update:modelValue", ["1", ""])
        await settle()
        expect(lastUpdate(wrapper)).toMatchObject({value: []})

        number.vm.$emit("update:modelValue", ["10", "5"])
        await settle()
        expect(lastUpdate(wrapper)).toMatchObject({value: []})

        number.vm.$emit("update:modelValue", ["1", "5"])
        await settle()
        expect(lastUpdate(wrapper)).toMatchObject({comparator: Comparators.BETWEEN, value: ["1", "5"], valueLabel: "1 - 5"})
    })
})

describe("FilterEditPopper comparator on a date range field", () => {
    const lastTriggeredKey: FilterKeyConfig = {
        key: "lastTriggered",
        label: "Last triggered",
        valueType: "time-range",
        customDateMode: "range",
        comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO],
        showComparatorSelection: true,
        valueProvider: async () => [{label: "Last 24 hours", value: "PT24H"}],
    }

    const mountRange = async (value: AppliedFilter["value"]) => {
        const wrapper = i18nMount(FilterEditPopper, {
            props: {
                filter: {id: "lt1", key: "lastTriggered", keyLabel: "Last triggered", comparator: Comparators.GREATER_THAN_OR_EQUAL_TO, comparatorLabel: "", value, valueLabel: ""},
                filterKey: lastTriggeredKey,
            },
        })
        await flushPromises()
        return wrapper
    }

    const isComparatorShown = (wrapper: Awaited<ReturnType<typeof mountRange>>) =>
        wrapper.findComponent(FilterComparatorSelect).props("shouldShowComparator")

    test("shouldHideTheComparatorWhenOnACustomRange", async () => {
        const wrapper = await mountRange({startDate: new Date("2026-01-15T10:00:00Z"), endDate: new Date("2026-01-20T18:00:00Z")})

        expect(isComparatorShown(wrapper)).toBe(false)
    })

    test("shouldShowTheComparatorAgainWhenSwitchingBackToPredefined", async () => {
        const wrapper = await mountRange({startDate: new Date("2026-01-15T10:00:00Z"), endDate: new Date("2026-01-20T18:00:00Z")})

        wrapper.findComponent(FilterSelect).vm.$emit("update:timeRangeMode", "predefined")
        await flushPromises()

        expect(isComparatorShown(wrapper)).toBe(true)
    })
})
