import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsDialog from "../../../src/components/Feedback/KsDialog.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsDialog", () => {
    test("renders when visible", () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, title: "Test Dialog"},
            slots: {default: "<p>Dialog content</p>"},
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("emits close event", async () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, title: "Test"},
            global: globalConfig,
        })
        wrapper.vm.$emit("close")
        expect(wrapper.emitted("close")).toBeTruthy()
    })

    test("uses 750px width when large", () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, large: true},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "ElDialog"}).props("width")).toBe("min(750px, 90vw)")
    })

    test("explicit width overrides large", () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, large: true, width: "60%"},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "ElDialog"}).props("width")).toBe("60%")
    })
})
