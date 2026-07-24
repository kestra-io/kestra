import {describe, test, expect} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import FilterEditPopper from "../../../../src/components/Data/KsDataTable/filter/layout/FilterEditPopper.vue"
import FilterSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterSelect.vue"
import FilterMultiSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterMultiSelect.vue"
import FilterComparatorSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterComparatorSelect.vue"
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
