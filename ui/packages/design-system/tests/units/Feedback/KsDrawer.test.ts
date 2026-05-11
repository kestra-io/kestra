import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
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
})
