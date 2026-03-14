import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import ElementPlus from "element-plus"
import KsButton from "../../../src/components/KsButton/KsButton.vue"
import KsButtonGroup from "../../../src/components/KsButtonGroup/KsButtonGroup.vue"

const globalConfig = {plugins: [ElementPlus]}

describe("KsButtonGroup", () => {
    test("renders a button group element", () => {
        const wrapper = mount(KsButtonGroup, {
            slots: {
                default: () => [
                    h(KsButton, {type: "primary"}, () => "Left"),
                    h(KsButton, {type: "primary"}, () => "Right"),
                ],
            },
            global: globalConfig,
        })
        expect(wrapper.find(".el-button-group").exists()).toBe(true)
    })

    test("renders slotted buttons inside the group", () => {
        const wrapper = mount(KsButtonGroup, {
            slots: {
                default: () => [
                    h(KsButton, null, () => "A"),
                    h(KsButton, null, () => "B"),
                    h(KsButton, null, () => "C"),
                ],
            },
            global: globalConfig,
        })
        expect(wrapper.findAll(".el-button").length).toBe(3)
    })

    test("vertical direction applies el-button-group--vertical class", () => {
        const wrapper = mount(KsButtonGroup, {
            props: {direction: "vertical"},
            slots: {default: () => [h(KsButton, null, () => "Top"), h(KsButton, null, () => "Bottom")]},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button-group--vertical").exists()).toBe(true)
    })

    test("type prop is passed through to the group", () => {
        const wrapper = mount(KsButtonGroup, {
            props: {type: "primary"},
            slots: {default: () => [h(KsButton, null, () => "X")]},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button-group").exists()).toBe(true)
    })

    test("size prop is passed through to the group", () => {
        const wrapper = mount(KsButtonGroup, {
            props: {size: "small"},
            slots: {default: () => [h(KsButton, null, () => "X")]},
            global: globalConfig,
        })
        expect(wrapper.find(".el-button-group").exists()).toBe(true)
    })
})
