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

// Icons are rendered as a `<use>` referencing a `<symbol>` in a pool shared across every
// KsTaskIcon instance (see KsTaskIcon.vue) rather than inlined directly under the wrapper —
// resolve the actual shape markup for a mounted instance via that shared pool.
function resolveSymbol(wrapper: ReturnType<typeof mount>) {
    const use = wrapper.find(".ks-task-icon__icon use")
    const id = use.attributes("href")?.replace("#", "")
    const symbol = id ? document.querySelector(`#ks-task-icon-pool #${id}`) : null
    return {use, id, symbol}
}

beforeEach(() => {
    // Reset HTML class to light mode before each test
    document.documentElement.className = ""
    // the icon pool is module-scoped (shared across every instance) — reset it between tests
    document.getElementById("ks-task-icon-pool")?.remove()
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
        expect(icon.find("svg").exists()).toBe(true)

        const {symbol} = resolveSymbol(wrapper)
        expect(symbol?.querySelector("circle")).not.toBeNull()
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

        const {symbol} = resolveSymbol(wrapper)
        expect(symbol?.querySelector("circle")?.getAttribute("fill")).toBe("currentColor")
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
        expect(icon.find("svg").exists()).toBe(true)

        const {symbol} = resolveSymbol(wrapper)
        expect(symbol?.querySelector("path")).not.toBeNull()
    })

    test("renders with customIcon prop", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: mockIconBase64}, onlyIcon: true},
            global: globalConfig,
        })
        const {symbol} = resolveSymbol(wrapper)
        expect(symbol?.querySelector("circle")).not.toBeNull()
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
        const {symbol} = resolveSymbol(wrapper)
        expect(symbol?.querySelector("circle")).not.toBeNull()
    })

    test("lazily resolves the icon via loadIcon when it isn't in icons", async () => {
        const loadIcon = vi.fn().mockResolvedValue({icon: mockIconBase64, flowable: false})
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        // renders the fallback icon while the request is in flight
        expect(resolveSymbol(wrapper).symbol?.querySelector("circle")).toBeNull()
        expect(loadIcon).toHaveBeenCalledWith("io.kestra.plugin.core.log.Log")

        await flushPromises()
        await wrapper.vm.$nextTick()

        expect(resolveSymbol(wrapper).symbol?.querySelector("circle")).not.toBeNull()
    })

    test("does not call loadIcon when the icon is already provided", () => {
        const loadIcon = vi.fn().mockResolvedValue({icon: mockIconBase64, flowable: false})
        mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        expect(loadIcon).not.toHaveBeenCalled()
    })

    test("reuses the same pooled symbol for repeated instances of the same icon", () => {
        const wrapperA = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const wrapperB = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })

        const {id: idA} = resolveSymbol(wrapperA)
        const {id: idB} = resolveSymbol(wrapperB)

        expect(idA).toBe(idB)
        expect(document.querySelectorAll(`#ks-task-icon-pool #${idA}`).length).toBe(1)
    })

    test("namespaces svg ids by icon type so two different icons sharing an internal id don't collide", () => {
        const gradientSvg = (color1: string, color2: string) =>
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
            `<linearGradient id="grad"><stop offset="0" stop-color="${color1}"/><stop offset="1" stop-color="${color2}"/></linearGradient>` +
            "<circle fill=\"url(#grad)\" cx=\"12\" cy=\"12\" r=\"10\"/></svg>"

        const iconsA = {"io.kestra.plugin.core.log.Log": {icon: btoa(gradientSvg("red", "yellow")), flowable: false}}
        const iconsB = {"io.kestra.plugin.core.flow.Parallel": {icon: btoa(gradientSvg("blue", "lime")), flowable: false}}

        const wrapperA = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: iconsA, onlyIcon: true},
            global: globalConfig,
        })
        const wrapperB = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.flow.Parallel", icons: iconsB, onlyIcon: true},
            global: globalConfig,
        })

        const {id: idA, symbol: symbolA} = resolveSymbol(wrapperA)
        const {id: idB, symbol: symbolB} = resolveSymbol(wrapperB)

        expect(idA).not.toBe(idB)

        const gradientIdA = symbolA?.querySelector("linearGradient")?.getAttribute("id")
        const gradientIdB = symbolB?.querySelector("linearGradient")?.getAttribute("id")

        expect(gradientIdA).not.toBe(gradientIdB)
        expect(symbolA?.querySelector("circle")?.getAttribute("fill")).toBe(`url(#${gradientIdA})`)
        expect(symbolB?.querySelector("circle")?.getAttribute("fill")).toBe(`url(#${gradientIdB})`)
    })

    test("strips event handler attributes from untrusted svg content", () => {
        const maliciousSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
            "<circle onclick=\"alert(1)\" cx=\"12\" cy=\"12\" r=\"10\"/></svg>"

        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: btoa(maliciousSvg)}, onlyIcon: true},
            global: globalConfig,
        })

        const {symbol} = resolveSymbol(wrapper)
        expect(symbol?.querySelector("circle")?.getAttribute("onclick")).toBeNull()
    })
})
