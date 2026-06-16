import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FilterChip from "../../../../src/components/Data/KsDataTable/filter/layout/FilterChip.vue"
import {Comparators, type AppliedFilter, type FilterKeyConfig} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const globalConfig = {
    plugins: [createI18n({legacy: false, locale: "en"})],
    stubs: {
        KsButton: true,
        KsTooltip: {template: "<div><slot /></div>"},
        FilterEditPopover: true,
    },
}

const levelKey: FilterKeyConfig = {
    key: "level",
    label: "Log Level",
    comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.EQUALS],
    comparatorLabels: {
        [Comparators.GREATER_THAN_OR_EQUAL_TO]: "At or Above",
        [Comparators.EQUALS]: "Equals",
    },
    exactEquals: true,
    valueType: "select",
}

const chip = (comparator: Comparators, comparatorLabel: string, value: string): AppliedFilter => ({
    id: "1",
    key: "level",
    keyLabel: "Log Level",
    comparator,
    comparatorLabel,
    value,
    valueLabel: value,
})

describe("FilterChip exactEquals", () => {
    test("hides the comparator label for an EQUALS chip, showing just the value", () => {
        const wrapper = mount(FilterChip, {
            props: {filter: chip(Comparators.EQUALS, "Equals", "DEBUG"), filterKey: levelKey},
            global: globalConfig,
        })

        expect(wrapper.find(".comparator").exists()).toBe(false)
        expect(wrapper.find(".value").text()).toBe("DEBUG")
    })

    test("still shows the comparator label for a threshold chip", () => {
        const wrapper = mount(FilterChip, {
            props: {filter: chip(Comparators.GREATER_THAN_OR_EQUAL_TO, "At or Above", "WARN"), filterKey: levelKey},
            global: globalConfig,
        })

        expect(wrapper.find(".comparator").text()).toBe("At or Above")
        expect(wrapper.find(".value").text()).toBe("WARN")
    })
})
