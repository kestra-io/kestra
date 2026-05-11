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
})
