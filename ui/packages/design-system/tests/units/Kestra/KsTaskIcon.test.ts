import {describe, test, expect, beforeEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
import DOMPurify from "dompurify"
import KestraDesignSystem from "../../../src/index"
import KsTaskIcon from "../../../src/components/Kestra/KsTaskIcon.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

// A simple circle SVG encoded as base64 to simulate a plugin icon
const mockSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"10\" fill=\"currentColor\"/></svg>"
const mockIconBase64 = btoa(mockSvg)

// A malicious SVG payload with an embedded <script> and an event handler
const maliciousSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" onload=\"alert(1)\">" +
    "<script>alert(1)</script><circle cx=\"12\" cy=\"12\" r=\"10\" fill=\"currentColor\" onclick=\"alert(2)\"/></svg>"
const maliciousIconBase64 = btoa(maliciousSvg)

// An icon with a <defs> gradient — the kind of id design tools commonly export as generic
// ("gradient0", "Layer_1", …), which can collide with another icon's def once both are inlined.
const gradientSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
    "<defs><linearGradient id=\"gradient0\"><stop offset=\"0\" stop-color=\"red\"/></linearGradient></defs>" +
    "<rect width=\"24\" height=\"24\" fill=\"url(#gradient0)\"/></svg>"
const gradientIconBase64 = btoa(gradientSvg)

const mockIcons = {
    "io.kestra.plugin.core.log.Log": {icon: mockIconBase64, flowable: false},
    "io.kestra.plugin.core.flow.Parallel": {icon: mockIconBase64, flowable: true},
    "io.kestra.plugin.malicious.Task": {icon: maliciousIconBase64, flowable: false},
    "io.kestra.plugin.gradient.Task": {icon: gradientIconBase64, flowable: false},
}

beforeEach(() => {
    // Reset HTML class to light mode before each test
    document.documentElement.className = ""
})

describe("KsTaskIcon", () => {
    test("renders wrapper element", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon").exists()).toBe(true)
    })

    test("renders icon element", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon__icon").exists()).toBe(true)
    })

    test("renders an inline svg element with the expected content", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.element.tagName.toLowerCase()).toBe("svg")
        expect(icon.attributes("viewBox")).toBe("0 0 24 24")
        expect(icon.find("circle").exists()).toBe(true)
        expect(icon.attributes("role")).toBe("img")
        expect(icon.attributes("aria-label")).toBe("io.kestra.plugin.core.log.Log")
    })

    test("strips script tags and event handlers from a malicious icon payload", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.malicious.Task", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.find("script").exists()).toBe(false)
        expect(icon.html()).not.toContain("<script")
        expect(icon.html()).not.toContain("onload")
        expect(icon.html()).not.toContain("onclick")
        // The rest of the (safe) markup should still render
        expect(icon.find("circle").exists()).toBe(true)
    })

    test("renders tooltip when onlyIcon is false", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: false},
            global: globalConfig,
        })
        // KsTooltip wraps the icon — the icon div should still be present
        expect(wrapper.find(".ks-task-icon__icon").exists()).toBe(true)
        // KsTooltip component should be rendered
        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.exists()).toBe(true)
    })

    test("renders icon as direct child when onlyIcon is true", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const directIcon = wrapper.find(".ks-task-icon > .ks-task-icon__icon")
        expect(directIcon.exists()).toBe(true)
    })

    test("applies flowable class when icon is flowable", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.flow.Parallel", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon--flowable").exists()).toBe(true)
    })

    test("does not apply flowable class when icon is not flowable", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon--flowable").exists()).toBe(false)
    })

    test("falls back to default icon when cls has no matching icon", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.unknown.Task", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        // Should still render with the fallback svg
        expect(icon.element.tagName.toLowerCase()).toBe("svg")
        expect(icon.find("path").exists()).toBe(true)
    })

    test("renders with customIcon prop", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: mockIconBase64}, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.element.tagName.toLowerCase()).toBe("svg")
        expect(icon.find("circle").exists()).toBe(true)
    })

    test("resolves inner class to parent when cls contains $", () => {
        const iconsWithParent = {
            "io.kestra.plugin.core.log.Log": {icon: mockIconBase64, flowable: false},
        }
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log$SubClass", icons: iconsWithParent, onlyIcon: true},
            global: globalConfig,
        })
        // Should resolve to parent class and find the icon
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.find("circle").exists()).toBe(true)
    })

    test("sanitizes a given icon only once across multiple instances", () => {
        // A never-before-mounted icon, so the module-level cache is guaranteed empty for it.
        const freshSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><polygon points=\"1,1 2,2 3,3\" fill=\"currentColor\"/></svg>"
        const freshIcons = {"io.kestra.plugin.fresh.Task": {icon: btoa(freshSvg), flowable: false}}

        const spy = vi.spyOn(DOMPurify, "sanitize")
        mount(KsTaskIcon, {props: {cls: "io.kestra.plugin.fresh.Task", icons: freshIcons, onlyIcon: true}, global: globalConfig})
        mount(KsTaskIcon, {props: {cls: "io.kestra.plugin.fresh.Task", icons: freshIcons, onlyIcon: true}, global: globalConfig})
        expect(spy).toHaveBeenCalledTimes(1)
        spy.mockRestore()
    })

    test("namespaces def ids per instance so two icons never share a gradient id", () => {
        const w1 = mount(KsTaskIcon, {props: {cls: "io.kestra.plugin.gradient.Task", icons: mockIcons, onlyIcon: true}, global: globalConfig})
        const w2 = mount(KsTaskIcon, {props: {cls: "io.kestra.plugin.gradient.Task", icons: mockIcons, onlyIcon: true}, global: globalConfig})

        const id1 = w1.find("linearGradient").attributes("id")
        const id2 = w2.find("linearGradient").attributes("id")

        expect(id1).toBeTruthy()
        expect(id1).not.toBe(id2)
        // The url(#...) reference must be rewritten to match its own instance's namespaced id
        expect(w1.find("rect").attributes("fill")).toBe(`url(#${id1})`)
        expect(w2.find("rect").attributes("fill")).toBe(`url(#${id2})`)
    })
})
