import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsSegmentedProgress from "../../../src/components/Data/KsSegmentedProgress.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSegmentedProgress", () => {
    test("renders one segment per entry with the value-proportional width", () => {
        const wrapper = mount(KsSegmentedProgress, {
            props: {
                total: 10,
                segments: [
                    {key: "SUCCESS", value: 6, color: "red"},
                    {key: "FAILED", value: 2, color: "blue"},
                ],
            },
            global: globalConfig,
        })
        const segments = wrapper.findAll(".ks-segmented-progress-segment")
        expect(segments).toHaveLength(2)
        expect(segments[0].attributes("style")).toContain("width: 60%")
        expect(segments[1].attributes("style")).toContain("width: 20%")
    })

    test("skips zero-value segments", () => {
        const wrapper = mount(KsSegmentedProgress, {
            props: {
                total: 10,
                segments: [
                    {key: "SUCCESS", value: 0, color: "red"},
                    {key: "FAILED", value: 2, color: "blue"},
                ],
            },
            global: globalConfig,
        })
        expect(wrapper.findAll(".ks-segmented-progress-segment")).toHaveLength(1)
    })

    test("exposes value and total sum via the default slot", () => {
        const wrapper = mount(KsSegmentedProgress, {
            props: {
                total: 10,
                segments: [
                    {key: "SUCCESS", value: 6, color: "red"},
                    {key: "FAILED", value: 1, color: "blue"},
                ],
            },
            slots: {default: "{{ params.value }} / {{ params.total }}"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-segmented-progress-label").text()).toBe("7 / 10")
    })

    test("sets aria attributes on the track for accessibility", () => {
        const wrapper = mount(KsSegmentedProgress, {
            props: {
                total: 10,
                segments: [{key: "SUCCESS", value: 4, color: "red"}],
            },
            global: globalConfig,
        })
        const track = wrapper.find(".ks-segmented-progress-track")
        expect(track.attributes("role")).toBe("progressbar")
        expect(track.attributes("aria-valuenow")).toBe("4")
        expect(track.attributes("aria-valuemin")).toBe("0")
        expect(track.attributes("aria-valuemax")).toBe("10")
    })

    test("does not render a track with zero total", () => {
        const wrapper = mount(KsSegmentedProgress, {
            props: {
                total: 0,
                segments: [{key: "SUCCESS", value: 4, color: "red"}],
            },
            global: globalConfig,
        })
        const segment = wrapper.find(".ks-segmented-progress-segment")
        expect(segment.attributes("style")).toContain("width: 0%")
    })

    test("does not render the label when no default slot is provided", () => {
        const wrapper = mount(KsSegmentedProgress, {
            props: {total: 10, segments: []},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-segmented-progress-label").exists()).toBe(false)
    })
})
