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

    test("small size applies modifier class", () => {
        const wrapper = mount(KsSteps, {
            props: {active: 0, size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-steps--small").exists()).toBe(true)
    })

    test("default size has no small modifier class", () => {
        const wrapper = mount(KsSteps, {
            props: {active: 0},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-steps--small").exists()).toBe(false)
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

    test("variant=bar renders one segment per step with state from status", () => {
        const wrapper = mount({
            components: {KsSteps, KsStep},
            template: `
                <ks-steps variant="bar" :active="1">
                    <ks-step title="A" status="success" />
                    <ks-step title="B" status="process" />
                    <ks-step title="C" status="wait" />
                </ks-steps>
            `,
        }, {global: globalConfig})

        const segs = wrapper.findAll(".ks-stepbar__seg")
        expect(segs.length).toBe(3)
        expect(segs[0].classes()).toContain("is-filled")
        expect(segs[1].classes()).toContain("is-active")
        expect(segs[2].classes()).toContain("is-upcoming")
        expect(segs[0].attributes("aria-label")).toBe("A")
        expect(wrapper.find(".kel-steps").exists()).toBe(false)
    })

    test("variant=bar treats missing/unknown status as upcoming", () => {
        const wrapper = mount({
            components: {KsSteps, KsStep},
            template: `
                <ks-steps variant="bar" :active="0">
                    <ks-step title="A" />
                    <ks-step title="B" status="finish" />
                </ks-steps>
            `,
        }, {global: globalConfig})
        const segs = wrapper.findAll(".ks-stepbar__seg")
        expect(segs[0].classes()).toContain("is-upcoming")
        expect(segs[1].classes()).toContain("is-filled")
    })

    test("default variant still renders ElSteps", () => {
        const wrapper = mount({
            components: {KsSteps, KsStep},
            template: "<ks-steps :active=\"0\"><ks-step title=\"A\" /></ks-steps>",
        }, {global: globalConfig})
        expect(wrapper.find(".kel-steps").exists()).toBe(true)
        expect(wrapper.find(".ks-stepbar").exists()).toBe(false)
    })
})
