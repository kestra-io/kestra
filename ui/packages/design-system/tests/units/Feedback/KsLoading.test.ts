import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import {vKsLoading} from "../../../src"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("vKsLoading", () => {
    test("mounts without errors when loading is false", () => {
        const wrapper = mount(
            {template: "<div v-ks-loading=\"false\">content</div>"},
            {global: globalConfig},
        )
        expect(wrapper.text()).toContain("content")
    })

    test("applies loading mask element when loading is true", () => {
        const wrapper = mount(
            {template: "<div v-ks-loading=\"true\" style=\"position:relative\">content</div>"},
            {global: globalConfig},
        )
        expect(wrapper.find(".kel-loading-mask").exists()).toBe(true)
    })

    test("does not render loading mask when loading is false", () => {
        const wrapper = mount(
            {template: "<div v-ks-loading=\"false\">content</div>"},
            {global: globalConfig},
        )
        expect(wrapper.find(".kel-loading-mask").exists()).toBe(false)
    })

    test("vKsLoading directive is the same as element-plus vLoading", async () => {
        const {vLoading} = await import("element-plus")
        expect(vKsLoading).toBe(vLoading)
    })
})
