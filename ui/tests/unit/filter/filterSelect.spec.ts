import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import ElementPlus, {ElDatePicker} from "element-plus"
import FilterSelect from "../../../src/components/filter/components/layout/FilterSelect.vue"

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, globalInjection: true}),
        ElementPlus,
    ],
}

const mountCustomTimeRange = () => mount(FilterSelect, {
    props: {
        modelValue: "",
        options: [],
        filterKey: {key: "timeRange"},
        timeRangeMode: "custom",
    },
    global: globalConfig,
})

describe("FilterSelect custom time range", () => {
    test("defaults the end date picker time part to now instead of midnight", () => {
        const before = Date.now()
        const wrapper = mountCustomTimeRange()
        const after = Date.now()

        const pickers = wrapper.findAllComponents(ElDatePicker)
        expect(pickers).toHaveLength(2)

        const endDefaultTime = pickers[1].props("defaultTime") as Date
        expect(endDefaultTime).toBeInstanceOf(Date)
        expect(endDefaultTime.getTime()).toBeGreaterThanOrEqual(before)
        expect(endDefaultTime.getTime()).toBeLessThanOrEqual(after)
    })

    test("leaves the start date picker without a default time so it keeps defaulting to midnight", () => {
        const wrapper = mountCustomTimeRange()

        const pickers = wrapper.findAllComponents(ElDatePicker)
        expect(pickers[0].props("defaultTime")).toBeUndefined()
    })
})
