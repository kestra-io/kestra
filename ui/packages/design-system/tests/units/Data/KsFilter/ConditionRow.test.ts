import {describe, test, expect} from "vitest"
import {nextTick} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import ConditionRow from "../../../../src/components/Data/KsDataTable/filter/ConditionRow.vue"
import FilterMultiSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterMultiSelect.vue"
import {Comparators, type AppliedFilter, type FilterKeyConfig} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})
const popoverStub = {template: "<div><slot name=\"reference\" /><slot /></div>"}
const globalConfig = {plugins: [i18n, KestraDesignSystem], stubs: {KsPopover: popoverStub}}

const multiKey: FilterKeyConfig = {
    key: "state",
    label: "State",
    valueType: "multi-select",
    comparators: [Comparators.IN],
    valueProvider: async () => [
        {label: "RUNNING", value: "RUNNING"},
        {label: "FAILED", value: "FAILED"},
    ],
}

const baseFilter: AppliedFilter = {
    id: "f1",
    key: "state",
    keyLabel: "State",
    comparator: Comparators.IN,
    comparatorLabel: "In",
    value: [],
    valueLabel: "",
}

const mountRow = (filter: AppliedFilter) =>
    mount(ConditionRow, {props: {filter, allKeys: [multiKey]}, global: globalConfig})

describe("ConditionRow multi-select commit on close", () => {
    test("commits a staged multi-select selection when unmounted (e.g. modal dismissed by overlay click)", async () => {
        const wrapper = mountRow(baseFilter)

        const multi = wrapper.findComponent(FilterMultiSelect)
        expect(multi.exists()).toBe(true)
        multi.vm.$emit("update:modelValue", ["RUNNING"])
        await nextTick()

        wrapper.unmount()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeTruthy()
        expect(updates!.at(-1)![0].value).toEqual(["RUNNING"])
    })

    test("does not emit on unmount when the multi-select draft is unchanged", async () => {
        const wrapper = mountRow({...baseFilter, value: ["RUNNING"], valueLabel: "RUNNING"})
        await nextTick()

        wrapper.unmount()

        expect(wrapper.emitted("update")).toBeFalsy()
    })
})
