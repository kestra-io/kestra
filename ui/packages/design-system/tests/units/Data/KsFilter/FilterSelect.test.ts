import {describe, test, expect} from "vitest"
import FilterSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterSelect.vue"
import KsDatePicker from "../../../../src/components/Form/KsDatePicker.vue"
import {i18nMount} from "../../i18nMount"

const globalConfig = {}

const mountCustomTimeRange = () => i18nMount(FilterSelect, {
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

        const pickers = wrapper.findAllComponents(KsDatePicker)
        expect(pickers).toHaveLength(2)

        const endDefaultTime = pickers[1].vm.$attrs.defaultTime as Date
        expect(endDefaultTime).toBeInstanceOf(Date)
        expect(endDefaultTime.getTime()).toBeGreaterThanOrEqual(before)
        expect(endDefaultTime.getTime()).toBeLessThanOrEqual(after)
    })

    test("leaves the start date picker without a default time so it keeps defaulting to midnight", () => {
        const wrapper = mountCustomTimeRange()

        const pickers = wrapper.findAllComponents(KsDatePicker)
        expect(pickers[0].vm.$attrs.defaultTime).toBeUndefined()
    })
})
