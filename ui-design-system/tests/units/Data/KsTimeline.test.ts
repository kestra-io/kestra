import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTimeline from "../../../src/components/Data/KsTimeline/KsTimeline.vue"
import KsTimelineItem from "../../../src/components/Data/KsTimeline/KsTimelineItem.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsTimeline", () => {
    test("renders timeline element", () => {
        const wrapper = mount(KsTimeline, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-timeline").exists()).toBe(true)
    })

    test("renders timeline items", () => {
        const wrapper = mount({
            components: {KsTimeline, KsTimelineItem},
            template: `
                <ks-timeline>
                    <ks-timeline-item timestamp="2024-01-01">Event 1</ks-timeline-item>
                    <ks-timeline-item timestamp="2024-01-02">Event 2</ks-timeline-item>
                </ks-timeline>
            `,
        }, {global: globalConfig})
        expect(wrapper.findAll(".kel-timeline-item").length).toBe(2)
    })

    test("timeline item renders content", () => {
        const wrapper = mount({
            components: {KsTimeline, KsTimelineItem},
            template: `
                <ks-timeline>
                    <ks-timeline-item timestamp="2024-01-01">Flow started</ks-timeline-item>
                </ks-timeline>
            `,
        }, {global: globalConfig})
        expect(wrapper.text()).toContain("Flow started")
    })

    test("large size timeline item", () => {
        const wrapper = mount({
            components: {KsTimeline, KsTimelineItem},
            template: `
                <ks-timeline>
                    <ks-timeline-item size="large" timestamp="2024-01-01">Large event</ks-timeline-item>
                </ks-timeline>
            `,
        }, {global: globalConfig})
        expect(wrapper.find(".kel-timeline-item").exists()).toBe(true)
    })
})
