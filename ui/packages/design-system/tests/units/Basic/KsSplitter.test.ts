import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import KestraDesignSystem from "../../../src/index"
import KsSplitter from "../../../src/components/Basic/KsSplitter/KsSplitter.vue"
import KsSplitterPanel from "../../../src/components/Basic/KsSplitter/KsSplitterPanel.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSplitter", () => {
    test("renders splitter element", () => {
        const wrapper = mount(KsSplitter, {
            slots: {
                default: defineComponent({
                    components: {KsSplitterPanel},
                    template: "<ks-splitter-panel>Panel</ks-splitter-panel>",
                }),
            },
            global: globalConfig,
        })
        expect(wrapper.find(".kel-splitter").exists()).toBe(true)
    })

    test("renders slot content", () => {
        const wrapper = mount(KsSplitter, {
            slots: {default: "<div class='panel-content'>Panel Content</div>"},
            global: globalConfig,
        })
        expect(wrapper.find(".panel-content").exists()).toBe(true)
    })
})

describe("KsSplitterPanel", () => {
    test("renders splitter panel element", () => {
        const wrapper = mount(KsSplitter, {
            slots: {
                default: defineComponent({
                    components: {KsSplitterPanel},
                    template: "<ks-splitter-panel><div>Content</div></ks-splitter-panel>",
                }),
            },
            global: globalConfig,
        })
        expect(wrapper.find(".kel-splitter-panel").exists()).toBe(true)
    })

    test("renders slot content", () => {
        const wrapper = mount(KsSplitter, {
            slots: {
                default: defineComponent({
                    components: {KsSplitterPanel},
                    template: "<ks-splitter-panel><p class='inner'>Inner</p></ks-splitter-panel>",
                }),
            },
            global: globalConfig,
        })
        expect(wrapper.find(".inner").exists()).toBe(true)
    })
})
