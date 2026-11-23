import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsLogoBadge from "./KsLogoBadge.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsLogoBadge", () => {
    test("renders the monogram glyph and the check badge", () => {
        const wrapper = mount(KsLogoBadge, {global: globalConfig})
        expect(wrapper.find(".ks-logo-badge__glyph").exists()).toBe(true)
        expect(wrapper.find(".ks-logo-badge__check").exists()).toBe(true)
    })
})
