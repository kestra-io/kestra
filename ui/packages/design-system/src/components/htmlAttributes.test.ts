import {afterEach, describe, expect, test} from "vitest"
import {defineComponent, h, nextTick, type Component} from "vue"
import {i18nMount} from "../../tests/units/i18nMount"
import * as DS from "../index"

type Case = [name: string, props?: Record<string, unknown>, wrap?: (child: () => ReturnType<typeof h>) => Component]

// Wrappers whose props type the HTML attributes with `@vue-ignore` and forward `$attrs` themselves.
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
]

let unmount: (() => void) | undefined
afterEach(() => unmount?.())

describe("HTML attributes on design-system wrappers", () => {
    test.each(CASES)("%s forwards them to the DOM", async (name, props = {}, wrap) => {
        const id = `html-attributes-${name}`
        const component = (DS as Record<string, unknown>)[name] as Component
        const child = () => h(component, {...props, id, "aria-label": name})
        const wrapper = i18nMount(wrap ? wrap(child) : defineComponent({render: child}), {attachTo: document.body})
        unmount = () => wrapper.unmount()
        await nextTick()

        const element = document.getElementById(id)
        expect(element).not.toBeNull()
        expect(document.querySelector(`[aria-label="${name}"]`)).not.toBeNull()
    })
})
