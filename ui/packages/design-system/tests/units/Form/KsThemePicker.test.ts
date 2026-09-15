import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsThemePicker from "../../../src/components/Form/KsThemePicker/KsThemePicker.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const OPTIONS = [
    {value: "dark-2", label: "Dark 2.0", preview: "dark-2" as const},
    {value: "dark", label: "Dark 1.0", preview: "dark" as const},
    {value: "light", label: "Light", preview: "light" as const},
    {value: "sync", label: "Sync with system", preview: "sync" as const},
]

describe("KsThemePicker", () => {
    test("renders one radio per option inside a radiogroup", () => {
        const wrapper = mount(KsThemePicker, {
            props: {modelValue: "light", options: OPTIONS},
            global: globalConfig,
        })
        expect(wrapper.find("[role='radiogroup']").exists()).toBe(true)
        expect(wrapper.findAll("[role='radio']")).toHaveLength(4)
    })

    test("marks only the selected option as checked", () => {
        const wrapper = mount(KsThemePicker, {
            props: {modelValue: "dark", options: OPTIONS},
            global: globalConfig,
        })
        const checked = wrapper.findAll("[role='radio']").map((radio) => radio.attributes("aria-checked"))
        expect(checked).toEqual(["false", "true", "false", "false"])
    })

    test("draws two overlaid previews for the sync option and one for the others", () => {
        const wrapper = mount(KsThemePicker, {
            props: {modelValue: "light", options: OPTIONS},
            global: globalConfig,
        })
        const previews = wrapper.findAll(".theme-picker__preview")
        expect(previews[0].findAll(".theme-window")).toHaveLength(1)
        expect(previews[3].findAll(".theme-window")).toHaveLength(2)
    })

    test("emits the chosen value", async () => {
        const wrapper = mount(KsThemePicker, {
            props: {modelValue: "light", options: OPTIONS},
            global: globalConfig,
        })
        await wrapper.findAll("[role='radio']")[1].trigger("click")
        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["dark"])
    })
})
