import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsPagination from "../../../src/components/Data/KsPagination.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsPagination", () => {
    test("renders pagination element", () => {
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, pageSize: 10},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-pagination").exists()).toBe(true)
    })

    test("background prop applies is-background class", () => {
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, background: true},
            global: globalConfig,
        })
        expect(wrapper.find(".is-background").exists()).toBe(true)
    })

    test("disabled prop applies is-disabled class", () => {
        const wrapper = mount(KsPagination, {
            props: {total: 100, currentPage: 1, disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".is-disabled").exists()).toBe(true)
    })
})
