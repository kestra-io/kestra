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

    test("renders a resize handle only when resizable", async () => {
        const withHandle = mount(KsDrawer, {
            props: {modelValue: true, resizable: true},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()
        expect(document.querySelectorAll(".kel-drawer__resize-handle").length).toBe(1)
        withHandle.unmount()

        const without = mount(KsDrawer, {
            props: {modelValue: true},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()
        expect(document.querySelectorAll(".kel-drawer__resize-handle").length).toBe(0)
        without.unmount()
    })
})
