import {describe, test, expect} from "vitest"
import ColumnToggleRow from "./ColumnToggleRow.vue"
import type {ColumnConfig} from "./composables/useTableColumns"
import {i18nMount} from "../../../../../tests/units/i18nMount"

const switchStub = {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<button role=\"switch\" :aria-checked=\"modelValue\" @click=\"$emit('update:modelValue', !modelValue)\" />",
}
const globalConfig = {stubs: {KsSwitch: switchStub}}

const column: ColumnConfig = {prop: "state", label: "State", description: "Current execution state", default: true}

const mountRow = (props: Partial<{column: ColumnConfig; checked: boolean; showHandle: boolean; showDescription: boolean}> = {}) =>
    i18nMount(ColumnToggleRow, {props: {column, checked: false, ...props}, global: globalConfig})

describe("ColumnToggleRow", () => {
    test("renders the column label", () => {
        const label = mountRow().find(".column-label")

        expect(label.exists()).toBe(true)
        expect(label.text()).toBe("State")
    })

    test("renders the drag handle only when showHandle is set", () => {
        expect(mountRow({showHandle: true}).find(".drag-handle").exists()).toBe(true)
        expect(mountRow({showHandle: false}).find(".drag-handle").exists()).toBe(false)
    })

    test("applies is-grouped exactly when showHandle is false", () => {
        expect(mountRow({showHandle: false}).find(".column-item").classes()).toContain("is-grouped")
        expect(mountRow({showHandle: true}).find(".column-item").classes()).not.toContain("is-grouped")
    })

    test("renders the description only when showDescription is true and the column has one", () => {
        const withDesc = mountRow({showDescription: true}).find(".column-text small")
        expect(withDesc.exists()).toBe(true)
        expect(withDesc.text()).toBe("Current execution state")

        expect(mountRow({showDescription: false}).find(".column-text small").exists()).toBe(false)
        expect(mountRow({column: {...column, description: undefined}, showDescription: true}).find(".column-text small").exists()).toBe(false)
    })

    test("defaults showHandle to false and showDescription to true", () => {
        const wrapper = mountRow()

        expect(wrapper.find(".drag-handle").exists()).toBe(false)
        expect(wrapper.find(".column-item").classes()).toContain("is-grouped")
        expect(wrapper.find(".column-text small").exists()).toBe(true)
    })

    test("the switch reflects checked", () => {
        expect(mountRow({checked: true}).find("[role=\"switch\"]").attributes("aria-checked")).toBe("true")
        expect(mountRow({checked: false}).find("[role=\"switch\"]").attributes("aria-checked")).toBe("false")
    })

    test("toggling the switch emits toggle", async () => {
        const wrapper = mountRow()

        await wrapper.find("[role=\"switch\"]").trigger("click")

        expect(wrapper.emitted("toggle")).toHaveLength(1)
    })

    test("the switch aria-label is the column label", () => {
        expect(mountRow().find("[role=\"switch\"]").attributes("aria-label")).toBe("State")
    })
})