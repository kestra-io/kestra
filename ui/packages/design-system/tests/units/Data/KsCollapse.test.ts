import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsCollapse from "../../../src/components/Data/KsCollapse/KsCollapse.vue"
import KsCollapseItem from "../../../src/components/Data/KsCollapse/KsCollapseItem.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsCollapse", () => {
    test("renders collapse element", () => {
        const wrapper = mount(KsCollapse, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-collapse").exists()).toBe(true)
    })

    test("renders collapse items", () => {
        const wrapper = mount({
            components: {KsCollapse, KsCollapseItem},
            template: `
                <ks-collapse model-value="1">
                    <ks-collapse-item title="Section 1" name="1">Content 1</ks-collapse-item>
                    <ks-collapse-item title="Section 2" name="2">Content 2</ks-collapse-item>
                </ks-collapse>
            `,
        }, {global: globalConfig})
        expect(wrapper.findAll(".kel-collapse-item").length).toBe(2)
    })

    test("disabled collapse item has is-disabled class", () => {
        const wrapper = mount({
            components: {KsCollapse, KsCollapseItem},
            template: `
                <ks-collapse model-value="1">
                    <ks-collapse-item title="Disabled" name="1" disabled>Content</ks-collapse-item>
                </ks-collapse>
            `,
        }, {global: globalConfig})
        expect(wrapper.find(".is-disabled").exists()).toBe(true)
    })

    test("accordion prop sets correct attribute", () => {
        const wrapper = mount(KsCollapse, {
            props: {accordion: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-collapse").exists()).toBe(true)
    })
})
