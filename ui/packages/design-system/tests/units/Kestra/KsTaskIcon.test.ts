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

function decodedSrc(src: string | undefined): string {
    const base64 = (src ?? "").replace("data:image/svg+xml;base64,", "")
    return atob(base64)
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

    test("renders icon element as an img with an accessible alt text", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.element.tagName).toBe("IMG")
        expect(icon.attributes("alt")).toBe("io.kestra.plugin.core.log.Log")
    })

    test("sets a data uri as the img src", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("src")).toContain("data:image/svg+xml;base64,")
        expect(decodedSrc(icon.attributes("src"))).toContain("<circle")
    })

    test("recolors currentColor in the svg via the variable prop", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true, variable: "--ks-text-error"},
            global: globalConfig,
        })
        const svg = decodedSrc(wrapper.find(".ks-task-icon__icon").attributes("src"))
        expect(svg).not.toContain("currentColor")
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
        expect(icon.attributes("src")).toContain("data:image/svg+xml;base64,")
    })

    test("renders with customIcon prop", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: mockIconBase64}, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(decodedSrc(icon.attributes("src"))).toContain("<circle")
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
        expect(decodedSrc(icon.attributes("src"))).toContain("<circle")
    })

    test("lazily resolves the icon via loadIcon when it isn't in icons", async () => {
        const loadIcon = vi.fn().mockResolvedValue({icon: mockIconBase64, flowable: false})
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        // renders the fallback icon while the request is in flight
        expect(decodedSrc(wrapper.find(".ks-task-icon__icon").attributes("src"))).not.toContain("<circle")
        expect(loadIcon).toHaveBeenCalledWith("io.kestra.plugin.core.log.Log")

        await flushPromises()
        await wrapper.vm.$nextTick()

        expect(decodedSrc(wrapper.find(".ks-task-icon__icon").attributes("src"))).toContain("<circle")
    })

    test("does not call loadIcon when the icon is already provided", () => {
        const loadIcon = vi.fn().mockResolvedValue({icon: mockIconBase64, flowable: false})
        mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        expect(loadIcon).not.toHaveBeenCalled()
    })
})
