import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTabs from "../../../src/components/Navigation/KsTabs/KsTabs.vue"
import KsTabPane from "../../../src/components/Navigation/KsTabs/KsTabPane.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsTabs", () => {
    test("renders tabs element", () => {
        const wrapper = mount(KsTabs, {
            props: {modelValue: "tab1"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tabs").exists()).toBe(true)
    })

    test("card type applies correct class", () => {
        const wrapper = mount(KsTabs, {
            props: {modelValue: "tab1", type: "card"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tabs--card").exists()).toBe(true)
    })

    test("renders tab panes", () => {
        const wrapper = mount({
            components: {KsTabs, KsTabPane},
            template: `
                <ks-tabs model-value="tab1">
                    <ks-tab-pane label="Tab 1" name="tab1">Content 1</ks-tab-pane>
                    <ks-tab-pane label="Tab 2" name="tab2">Content 2</ks-tab-pane>
                </ks-tabs>
            `,
        }, {global: globalConfig})
        expect(wrapper.find(".kel-tabs").exists()).toBe(true)
        expect(wrapper.findAll(".kel-tabs__item").length).toBeGreaterThan(0)
    })

    test("disabled tab pane has is-disabled class", () => {
        const wrapper = mount({
            components: {KsTabs, KsTabPane},
            template: `
                <ks-tabs model-value="tab1">
                    <ks-tab-pane label="Tab 1" name="tab1">Content 1</ks-tab-pane>
                    <ks-tab-pane label="Disabled" name="tab2" disabled>Disabled</ks-tab-pane>
                </ks-tabs>
            `,
        }, {global: globalConfig})
        expect(wrapper.find(".is-disabled").exists()).toBe(true)
    })
})
