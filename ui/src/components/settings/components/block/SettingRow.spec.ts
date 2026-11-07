import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import SettingRow from "./SettingRow.vue"

const mountSettingRow = (props: {[key: string]: unknown} = {}, slots: {[key: string]: string} = {}) =>
    i18nMount(SettingRow, {props, slots})

describe("SettingRow", () => {
    test("renders the label in the label block", () => {
        const wrapper = mountSettingRow({label: "Maximum retention"})

        expect(wrapper.get(".setting-row__label").text()).toBe("Maximum retention")
    })

    test("shows the required asterisk only when required is set", () => {
        const wrapper = mountSettingRow({label: "Maximum retention", required: true})

        expect(wrapper.get(".setting-row__required").text()).toBe("*")

        const optional = mountSettingRow({label: "Maximum retention"})

        expect(optional.find(".setting-row__required").exists()).toBe(false)
    })

    test("renders the description block from the description prop", () => {
        const wrapper = mountSettingRow({
            label: "Maximum retention",
            description: "How long processed executions are stored",
        })

        expect(wrapper.get(".setting-row__description").text()).toBe("How long processed executions are stored")
    })

    test("renders the description slot in place of the prop", () => {
        const wrapper = mountSettingRow(
            {label: "Maximum retention", description: "From the prop"},
            {description: "From the slot"},
        )

        expect(wrapper.get(".setting-row__description").text()).toBe("From the slot")
    })

    test("renders the description block when only the slot is set", () => {
        const wrapper = mountSettingRow({label: "Maximum retention"}, {description: "From the slot"})

        expect(wrapper.get(".setting-row__description").text()).toBe("From the slot")
    })

    test("renders no description block without a description prop or slot", () => {
        const wrapper = mountSettingRow({label: "Maximum retention"})

        expect(wrapper.find(".setting-row__description").exists()).toBe(false)
    })

    test("adds the stacked modifier class only when stacked is set", () => {
        const stacked = mountSettingRow({label: "Maximum retention", stacked: true})

        expect(stacked.classes()).toContain("setting-row--stacked")

        const inline = mountSettingRow({label: "Maximum retention"})

        expect(inline.classes()).not.toContain("setting-row--stacked")
    })

    test("renders the default slot in the control area", () => {
        const wrapper = mountSettingRow({label: "Maximum retention"}, {default: "<p>30 days</p>"})

        expect(wrapper.get(".setting-row__control").get("p").text()).toBe("30 days")
    })
})
