import {describe, test, expect, beforeEach, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTaskIcon from "../../../src/components/Kestra/KsTaskIcon.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

// A simple circle SVG encoded as base64 to simulate a plugin icon
const mockSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"10\" fill=\"currentColor\"/></svg>"
const mockIconBase64 = btoa(mockSvg)

const mockIcons = {
    "io.kestra.plugin.core.log.Log": {icon: mockIconBase64, flowable: false},
    "io.kestra.plugin.core.flow.Parallel": {icon: mockIconBase64, flowable: true},
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

    test("renders an inline, inspectable svg instead of a background image", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("style") ?? "").not.toContain("background-image")
        expect(icon.find("svg > circle").exists()).toBe(true)
    })

    test("exposes an accessible role and label", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("role")).toBe("img")
        expect(icon.attributes("aria-label")).toBe("io.kestra.plugin.core.log.Log")
        expect(icon.find("svg").attributes("aria-hidden")).toBe("true")
    })

    test("recolors via CSS currentColor inheritance instead of rewriting the svg", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true, variable: "--ks-text-error"},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("style")).toContain("color: var(--ks-text-error)")
        expect(icon.find("circle").attributes("fill")).toBe("currentColor")
    })

    test("renders tooltip when onlyIcon is false", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: false},
            global: globalConfig,
        })
        // KsTooltip wraps the icon — the icon element should still be present
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
        expect(icon.find("svg > path").exists()).toBe(true)
    })

    test("renders with customIcon prop", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: mockIconBase64}, onlyIcon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon__icon circle").exists()).toBe(true)
    })

    test("resolves inner class to parent when cls contains $", () => {
        const iconsWithParent = {
            "io.kestra.plugin.core.log.Log": {icon: mockIconBase64, flowable: false},
        }
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log$SubClass", icons: iconsWithParent, onlyIcon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon__icon circle").exists()).toBe(true)
    })

    test("lazily resolves the icon via loadIcon when it isn't in icons", async () => {
        const loadIcon = vi.fn().mockResolvedValue({icon: mockIconBase64, flowable: false})
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        // renders the fallback icon while the request is in flight
        expect(wrapper.find(".ks-task-icon__icon circle").exists()).toBe(false)
        expect(loadIcon).toHaveBeenCalledWith("io.kestra.plugin.core.log.Log")

        await flushPromises()
        await wrapper.vm.$nextTick()

        expect(wrapper.find(".ks-task-icon__icon circle").exists()).toBe(true)
    })

    test("does not call loadIcon when the icon is already provided", () => {
        const loadIcon = vi.fn().mockResolvedValue({icon: mockIconBase64, flowable: false})
        mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        expect(loadIcon).not.toHaveBeenCalled()
    })

    test("keeps defs fully inline per instance so currentColor/theme context stays correct", () => {
        // a <defs> shared out-of-context would ignore each instance's own color: verified this
        // breaks in a real browser (a shared <stop stop-color="currentColor"> renders the same
        // wrong color everywhere, ignoring each instance's `variable`/theme). Every instance must
        // therefore keep its own private copy of its icon's defs.
        const gradientSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
            "<defs><linearGradient id=\"grad\"><stop stop-color=\"currentColor\"/></linearGradient></defs>" +
            "<circle fill=\"url(#grad)\" cx=\"12\" cy=\"12\" r=\"10\"/></svg>"
        const icons = {"io.kestra.plugin.core.log.Log": {icon: btoa(gradientSvg), flowable: false}}

        const wrapperA = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons, onlyIcon: true, variable: "--ks-text-error"},
            global: globalConfig,
        })
        const wrapperB = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons, onlyIcon: true},
            global: globalConfig,
        })

        // each instance owns its own <linearGradient> — not a single shared/pooled node
        const gradientA = wrapperA.find("linearGradient")
        const gradientB = wrapperB.find("linearGradient")
        expect(gradientA.exists()).toBe(true)
        expect(gradientB.exists()).toBe(true)
        expect(gradientA.element).not.toBe(gradientB.element)

        // ...uniquely namespaced per instance, so their ids don't collide with each other
        expect(gradientA.attributes("id")).not.toBe(gradientB.attributes("id"))
        expect(wrapperA.find("circle").attributes("fill")).toBe(`url(#${gradientA.attributes("id")})`)
        expect(wrapperB.find("circle").attributes("fill")).toBe(`url(#${gradientB.attributes("id")})`)
    })

    test("namespaces css classes per instance so generic tool-exported class names (st0, cls-1, ...) don't collide", () => {
        // mirrors real Illustrator/Figma-exported icons: a <style> block styling elements via a
        // generic class name, reused verbatim across unrelated icons
        const classStyledSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
            "<style>.st0{fill:currentColor;}</style><circle class=\"st0\" cx=\"12\" cy=\"12\" r=\"10\"/></svg>"
        const icons = {"io.kestra.plugin.core.log.Log": {icon: btoa(classStyledSvg), flowable: false}}

        const wrapperA = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons, onlyIcon: true},
            global: globalConfig,
        })
        const wrapperB = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons, onlyIcon: true},
            global: globalConfig,
        })

        const classA = wrapperA.find("circle").attributes("class")
        const classB = wrapperB.find("circle").attributes("class")
        expect(classA).not.toBe(classB)

        // each instance's own <style> block matches its own (differently namespaced) class
        expect(wrapperA.find("style").text()).toContain(`.${classA}{fill:currentColor;}`)
        expect(wrapperB.find("style").text()).toContain(`.${classB}{fill:currentColor;}`)
    })

    test("strips event handler attributes from untrusted svg content", () => {
        const maliciousSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
            "<circle onclick=\"alert(1)\" cx=\"12\" cy=\"12\" r=\"10\"/></svg>"

        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: btoa(maliciousSvg)}, onlyIcon: true},
            global: globalConfig,
        })

        expect(wrapper.find("circle").attributes("onclick")).toBeUndefined()
    })
})
