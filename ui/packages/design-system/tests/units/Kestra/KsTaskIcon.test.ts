import {describe, test, expect, beforeEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
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

function decodeImgSrc(src: string): string {
    const base64 = src.slice("data:image/svg+xml;base64,".length)
    const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0))
    return new TextDecoder().decode(bytes)
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

    test("renders the icon as an <img> with a data: src containing the expected content", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.element.tagName.toLowerCase()).toBe("img")

        const src = icon.attributes("src") ?? ""
        expect(src.startsWith("data:image/svg+xml;base64,")).toBe(true)
        expect(decodeImgSrc(src)).toContain("<circle")
    })

    test("uses the cls as the accessible name when onlyIcon is true", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon__icon").attributes("alt")).toBe("io.kestra.plugin.core.log.Log")
    })

    test("renders tooltip when onlyIcon is false, with an empty (decorative) alt", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: false},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.exists()).toBe(true)
        expect(icon.attributes("alt")).toBe("")
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

    test("falls back to the default icon when cls has no matching icon", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.unknown.Task", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const src = wrapper.find(".ks-task-icon__icon").attributes("src") ?? ""
        expect(decodeImgSrc(src)).toContain("<path")
    })

    test("renders with customIcon prop", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: mockIconBase64}, onlyIcon: true},
            global: globalConfig,
        })
        const src = wrapper.find(".ks-task-icon__icon").attributes("src") ?? ""
        expect(decodeImgSrc(src)).toContain("<circle")
    })

    test("resolves inner class to parent when cls contains $", () => {
        const iconsWithParent = {
            "io.kestra.plugin.core.log.Log": {icon: mockIconBase64, flowable: false},
        }
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log$SubClass", icons: iconsWithParent, onlyIcon: true},
            global: globalConfig,
        })
        const src = wrapper.find(".ks-task-icon__icon").attributes("src") ?? ""
        expect(decodeImgSrc(src)).toContain("<circle")
    })

    test("builds the src only once for the same icon across multiple instances", () => {
        // A never-before-mounted icon, so the module-level cache is guaranteed empty for it.
        const freshSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><polygon points=\"1,1 2,2 3,3\" fill=\"currentColor\"/></svg>"
        const freshIcons = {"io.kestra.plugin.fresh.Task": {icon: btoa(freshSvg), flowable: false}}

        const spy = vi.spyOn(globalThis, "atob")
        const w1 = mount(KsTaskIcon, {props: {cls: "io.kestra.plugin.fresh.Task", icons: freshIcons, onlyIcon: true}, global: globalConfig})
        const callsAfterFirst = spy.mock.calls.length
        const w2 = mount(KsTaskIcon, {props: {cls: "io.kestra.plugin.fresh.Task", icons: freshIcons, onlyIcon: true}, global: globalConfig})
        const callsAfterSecond = spy.mock.calls.length
        spy.mockRestore()

        expect(callsAfterSecond).toBe(callsAfterFirst)
        expect(w1.find(".ks-task-icon__icon").attributes("src")).toBe(w2.find(".ks-task-icon__icon").attributes("src"))
    })
})
