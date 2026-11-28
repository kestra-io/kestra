import {afterEach, describe, expect, test} from "vitest"
import {defineComponent, h, nextTick, type Component} from "vue"
import {i18nMount} from "../../tests/units/i18nMount"
import * as DS from "../index"

type Case = [name: string, props?: Record<string, unknown>, wrap?: (child: () => ReturnType<typeof h>) => Component, forwardsId?: boolean]

const CASES: Case[] = [
    ["KsButton"],
    ["KsIconButton"],
    ["KsSwitch"],
    ["KsAlert", {title: "alert"}],
    ["KsDialog", {modelValue: true}],
    ["KsTag"],
    ["KsSearch"],
    ["KsInput"],
    ["KsInputNumber"],
    ["KsIcon"],
    ["KsCheckTag"],
    ["KsSelect"],
    ["KsExecutionStatus", {status: "SUCCESS"}],
    ["KsCheckbox"],
    ["KsScrollbar"],
    ["KsSegmented", {options: ["a"]}],
    ["KsCard"],
    ["KsRadioGroup"],
    ["KsDataTable", {loadData: async () => ({results: [], total: 0}), currentPage: 1, pageSize: 25}],
    ["KsSplitterPanel", {}, (child) => defineComponent({render: () => h(DS.KsSplitter, null, () => child())})],
    ["KsDropdownItem", {}, (child) => defineComponent({render: () => h(DS.KsDropdown, {persistent: true}, {default: () => h("button"), dropdown: () => h(DS.KsDropdownMenu, null, () => child())})})],
    ["KsSideBar"],
    ["KsNoData"],
    ["KsDrillRow", {label: "row"}],
    ["KsAvatar"],
    ["KsLink"],
    ["KsText"],
    ["KsSideBarItem", {title: "item"}],
    ["KsTree"],
    ["KsRow"],
    ["KsSplitter"],
    ["KsPagination", {total: 10}],
    ["KsProgress", {percentage: 50}],
    ["KsTable"],
    ["KsDrawer", {modelValue: true}],
    ["KsPopover", {visible: true, teleported: false}, undefined, false],
    ["KsAutocomplete"],
    ["KsDatePicker"],
    ["KsTimePicker"],
    ["KsForm"],
    ["KsFormItem"],
    ["KsTabs"],
    ["KsTabPane", {name: "a", label: "a"}, (child) => defineComponent({render: () => h(DS.KsTabs, {modelValue: "a"}, () => child())})],
]

let unmount: (() => void) | undefined
afterEach(() => unmount?.())

describe("HTML attributes on design-system wrappers", () => {
    test.each(CASES)("%s forwards them to the DOM", async (name, props = {}, wrap, forwardsId = true) => {
        const id = `html-attributes-${name}`
        const component = (DS as Record<string, unknown>)[name] as Component
        const child = () => h(component, {...props, id, "aria-label": name})
        const wrapper = i18nMount(wrap ? wrap(child) : defineComponent({render: child}), {attachTo: document.body})
        unmount = () => wrapper.unmount()
        await nextTick()

        if (forwardsId) {
            expect(document.getElementById(id)).not.toBeNull()
        }
        expect(document.querySelector(`[aria-label="${name}"]`)).not.toBeNull()
    })

    test("KsCheckboxButton forwards title to its label", async () => {
        const wrapper = i18nMount(defineComponent({render: () => h(DS.KsCheckboxButton, {value: "case", title: "Match case"}, () => "Aa")}), {attachTo: document.body})
        unmount = () => wrapper.unmount()
        await nextTick()

        expect(document.querySelector("label[title=\"Match case\"]")).not.toBeNull()
    })
})
