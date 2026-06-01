import {describe, test, expect} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsDrawer from "../../../src/components/Feedback/KsDrawer.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsDrawer", () => {
    test("renders when visible", () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true},
            slots: {default: "<p>Drawer content</p>"},
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("emits update:modelValue event", async () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true},
            global: globalConfig,
        })
        wrapper.vm.$emit("update:modelValue", false)
        expect(wrapper.emitted("update:modelValue")).toBeTruthy()
    })

    test("reflects full-screen state in the toggle icon when resizable", async () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true, resizable: true, title: "Diff"},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()

        const expandIcon = document.querySelector(".kel-drawer__header .arrow-expand-icon")
        expect(expandIcon).toBeTruthy()

        const toggle = expandIcon!.closest("button") as HTMLElement
        toggle.click()
        await flushPromises()

        expect(document.querySelector(".kel-drawer__header .arrow-collapse-icon")).toBeTruthy()
        wrapper.unmount()
    })
})
