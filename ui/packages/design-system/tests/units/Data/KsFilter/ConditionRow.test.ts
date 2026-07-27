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

const rangeAndSetKey: FilterKeyConfig = {
    key: "level",
    label: "Level",
    valueType: "multi-select",
    comparators: [
        Comparators.GREATER_THAN_OR_EQUAL_TO,
        Comparators.LESS_THAN_OR_EQUAL_TO,
        Comparators.IN,
        Comparators.NOT_IN,
    ],
    valueProvider: async () => [
        {label: "WARN", value: "WARN"},
        {label: "ERROR", value: "ERROR"},
    ],
}

const mountLevelRow = (filter: AppliedFilter) =>
    mount(ConditionRow, {props: {filter, allKeys: [rangeAndSetKey]}, global: globalConfig})

describe("ConditionRow range comparators on a multi-select field", () => {
    test("renders a plain select (not the multi-select popover) for GREATER_THAN_OR_EQUAL_TO", () => {
        const wrapper = mountLevelRow({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            comparatorLabel: "At or Above",
            value: "WARN",
            valueLabel: "WARN",
        })

        expect(wrapper.findComponent(FilterMultiSelect).exists()).toBe(false)
        expect(wrapper.find(".cond-value-select").exists()).toBe(true)
    })

    test("renders the multi-select popover for IN", () => {
        const wrapper = mountLevelRow({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: ["WARN", "ERROR"],
            valueLabel: "WARN, ERROR",
        })

        expect(wrapper.findComponent(FilterMultiSelect).exists()).toBe(true)
        expect(wrapper.find(".cond-value-select").exists()).toBe(false)
    })

    test("resets a stale multi-value when switching from IN to GREATER_THAN_OR_EQUAL_TO", async () => {
        const wrapper = mountLevelRow({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.IN,
            comparatorLabel: "In",
            value: ["WARN", "ERROR"],
            valueLabel: "WARN, ERROR",
        })

        const opSelect = wrapper.findComponent(".cond-op")
        opSelect.vm.$emit("update:modelValue", Comparators.GREATER_THAN_OR_EQUAL_TO)
        await nextTick()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeTruthy()
        expect(updates!.at(-1)![0]).toMatchObject({
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            value: "",
            valueLabel: "",
        })
    })

    test("resets a stale scalar value when switching from GREATER_THAN_OR_EQUAL_TO to IN", async () => {
        const wrapper = mountLevelRow({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            comparatorLabel: "At or Above",
            value: "WARN",
            valueLabel: "WARN",
        })

        const opSelect = wrapper.findComponent(".cond-op")
        opSelect.vm.$emit("update:modelValue", Comparators.IN)
        await nextTick()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeTruthy()
        expect(updates!.at(-1)![0]).toMatchObject({
            comparator: Comparators.IN,
            value: [],
            valueLabel: "",
        })
    })

    test("keeps the value when switching between the two range comparators", async () => {
        const wrapper = mountLevelRow({
            id: "f1",
            key: "level",
            keyLabel: "Level",
            comparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
            comparatorLabel: "At or Above",
            value: "WARN",
            valueLabel: "WARN",
        })

        const opSelect = wrapper.findComponent(".cond-op")
        opSelect.vm.$emit("update:modelValue", Comparators.LESS_THAN_OR_EQUAL_TO)
        await nextTick()

        const updates = wrapper.emitted("update") as Array<[AppliedFilter]> | undefined
        expect(updates).toBeTruthy()
        expect(updates!.at(-1)![0]).toMatchObject({
            comparator: Comparators.LESS_THAN_OR_EQUAL_TO,
            value: "WARN",
        })
    })
})

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
