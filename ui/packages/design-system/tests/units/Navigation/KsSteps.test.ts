import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsSteps from "../../../src/components/Navigation/KsSteps/KsSteps.vue"
import KsStep from "../../../src/components/Navigation/KsSteps/KsStep.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSteps", () => {
    test("renders steps element", () => {
        const wrapper = mount(KsSteps, {
            props: {active: 0},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-steps").exists()).toBe(true)
    })

    test("vertical direction applies correct class", () => {
        const wrapper = mount(KsSteps, {
            props: {active: 0, direction: "vertical"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-steps--vertical").exists()).toBe(true)
    })

    test("renders step items", () => {
        const wrapper = mount({
            components: {KsSteps, KsStep},
            template: `
                <ks-steps :active="1">
                    <ks-step title="Step 1" />
                    <ks-step title="Step 2" />
                    <ks-step title="Step 3" />
                </ks-steps>
            `,
        }, {global: globalConfig})
        expect(wrapper.findAll(".kel-step").length).toBe(3)
    })
})
