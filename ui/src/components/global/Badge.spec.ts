import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem, {KsTag, KsTooltip} from "@kestra-io/design-system"
import Badge from "./Badge.vue"

const mountBadge = (props: {label: string; tooltip?: string}) =>
    mount(Badge, {
        props,
        global: {
            plugins: [KestraDesignSystem],
        },
    })

describe("Badge", () => {
    it("renders the label on the tag", () => {
        const wrapper = mountBadge({label: "v1.0.0"})

        const tag = wrapper.findComponent(KsTag)
        expect(tag.exists()).toBe(true)
        expect(tag.props("label")).toBe("v1.0.0")
        expect(wrapper.text()).toContain("v1.0.0")
    })

    it("uses tooltip as the tooltip content when given", () => {
        const wrapper = mountBadge({label: "v1.0.0", tooltip: "Release version"})

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.props("content")).toBe("Release version")
    })

    it("falls back to label when tooltip is omitted", () => {
        const wrapper = mountBadge({label: "v1.0.0"})

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(true)
        expect(tooltip.props("content")).toBe("v1.0.0")
    })

    it("renders the tag with type='primary', size='small' and round", () => {
        const wrapper = mountBadge({label: "v1.0.0"})

        const tag = wrapper.findComponent(KsTag)
        expect(tag.exists()).toBe(true)
        expect(tag.props("type")).toBe("primary")
        expect(tag.props("size")).toBe("small")
        expect(tag.props("round")).toBe(true)
    })
})
