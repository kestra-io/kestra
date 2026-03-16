import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTooltip from "../../../src/components/Feedback/KsTooltip.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsTooltip", () => {
    test("renders tooltip trigger element", () => {
        const wrapper = mount(KsTooltip, {
            props: {content: "Test tooltip"},
            slots: {default: "<button>Hover me</button>"},
            global: globalConfig,
        })
        expect(wrapper.element.childElementCount > 0 || wrapper.html().includes("button")).toBe(true)
    })

    test("disabled prop is accepted", () => {
        const wrapper = mount(KsTooltip, {
            props: {content: "Test", disabled: true},
            slots: {default: "<button>Hover me</button>"},
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })
})
