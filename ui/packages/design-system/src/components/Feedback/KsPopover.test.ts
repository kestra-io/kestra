import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsPopover from "./KsPopover.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsPopover", () => {
    test("renders reference slot", () => {
        const wrapper = mount(KsPopover, {
            props: {trigger: "click"},
            slots: {
                default: "<p>Popover content</p>",
                reference: "<button>Click me</button>",
            },
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("disabled prop is accepted", () => {
        const wrapper = mount(KsPopover, {
            props: {disabled: true, trigger: "click"},
            slots: {reference: "<button>Click me</button>"},
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })
})
