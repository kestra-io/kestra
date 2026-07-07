import {describe, test, expect, beforeEach, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTaskIcon from "../../../src/components/Kestra/KsTaskIcon.vue"
import fallbackIcon from "../../../src/assets/images/plugin-icon-fallback.svg"

const globalConfig = {plugins: [KestraDesignSystem]}

const mockIcons = {
    "io.kestra.plugin.core.log.Log": {flowable: false, monochrome: false, hasIcon: true},
    "io.kestra.plugin.core.flow.Parallel": {flowable: true, monochrome: false, hasIcon: true},
    "io.kestra.plugin.core.debug.Echo": {flowable: false, monochrome: true, hasIcon: true},
    // registered (present in the catalog, with a real `flowable` value) but ships no icon file —
    // every task/trigger class gets an entry regardless of icon presence
    "io.kestra.plugin.core.debug.NoIcon": {flowable: false, monochrome: false, hasIcon: false},
    // sourced from the external ecosystem plugin catalog (not installed locally) — pre-resolved
    // by the caller since this instance can't serve it via the local /icon.svg endpoint
    "io.kestra.plugin.scripts.python.Commands": {
        flowable: false,
        monochrome: false,
        hasIcon: true,
        iconUrl: "data:image/svg+xml;base64,mockbase64",
    },
}

function svgUrlFor(cls: string): string {
    return `/api/v1/plugins/icons/${encodeURIComponent(cls)}/icon.svg`
}

beforeEach(() => {
    // Reset HTML class to light mode before each test
    document.documentElement.className = ""
    delete (window as any).KESTRA_BASE_PATH
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

    test("points a non-monochrome icon's img src at the real, cacheable svg endpoint", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("src")).toBe(svgUrlFor("io.kestra.plugin.core.log.Log"))
    })

    test("falls back to the local asset for a registered class that has no icon at all", () => {
        // Regression: every registered task/trigger class gets an `icons` map entry (other
        // consumers rely on `flowable` being present even without an icon) — being present in the
        // map must NOT be treated as "has an icon", or the <img> points at a URL that 404s.
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.debug.NoIcon", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("src")).toBe(fallbackIcon)
    })

    test("uses the pre-resolved iconUrl instead of the local endpoint for ecosystem-catalog icons", () => {
        // This instance can't serve icons for plugins it doesn't have installed — the caller
        // (pluginsStore) pre-resolves a data URI for these instead of a local /icon.svg URL.
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.scripts.python.Commands", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("src")).toBe("data:image/svg+xml;base64,mockbase64")
    })

    test("prefixes the svg endpoint with KESTRA_BASE_PATH when the app is served behind a subpath", () => {
        (window as any).KESTRA_BASE_PATH = "/kestra"
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("src")).toBe(`/kestra${svgUrlFor("io.kestra.plugin.core.log.Log")}`)
    })

    test("does not produce a protocol-relative // url when KESTRA_BASE_PATH is the root path", () => {
        // A trailing slash on the base path (the default for a root-hosted deployment) must not
        // survive into the URL — "/" + "/api/..." = "//api/..." is parsed by browsers as a
        // protocol-relative URL with host "api", not an absolute path.
        (window as any).KESTRA_BASE_PATH = "/"
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        const src = icon.attributes("src") ?? ""
        expect(src).toBe(svgUrlFor("io.kestra.plugin.core.log.Log"))
        expect(src.startsWith("//")).toBe(false)
    })

    test("renders a monochrome icon as a CSS mask instead of an img", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.debug.Echo", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.element.tagName).toBe("DIV")
        expect(icon.attributes("style")).toContain(`mask-image: url("${svgUrlFor("io.kestra.plugin.core.debug.Echo")}")`)
    })

    test("recolors a monochrome icon via the variable prop", () => {
        vi.spyOn(window, "getComputedStyle").mockReturnValue({
            getPropertyValue: (prop: string) => (prop === "--ks-text-error" ? "red" : ""),
        } as CSSStyleDeclaration)

        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.debug.Echo", icons: mockIcons, onlyIcon: true, variable: "--ks-text-error"},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("style")).toContain("background-color: red")

        vi.restoreAllMocks()
    })

    test("falls back to --ks-text-primary when no variable prop is given", () => {
        vi.spyOn(window, "getComputedStyle").mockReturnValue({
            getPropertyValue: (prop: string) => (prop === "--ks-text-primary" ? "blue" : ""),
        } as CSSStyleDeclaration)

        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.debug.Echo", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("style")).toContain("background-color: blue")

        vi.restoreAllMocks()
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

    test("applies flowable class even for a registered class without an icon", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {
                cls: "io.kestra.plugin.core.debug.NoIcon",
                icons: {"io.kestra.plugin.core.debug.NoIcon": {flowable: true, monochrome: false, hasIcon: false}},
                onlyIcon: true,
            },
            global: globalConfig,
        })
        expect(wrapper.find(".ks-task-icon--flowable").exists()).toBe(true)
    })

    test("falls back to the local fallback asset when cls has no matching icon", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.unknown.Task", icons: mockIcons, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("src")).toBe(fallbackIcon)
    })

    test("renders with customIcon prop as a plain img pointed at the given url", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: "https://example.com/custom.svg"}, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.element.tagName).toBe("IMG")
        expect(icon.attributes("src")).toBe("https://example.com/custom.svg")
    })

    test("renders customIcon as a mask when flagged monochrome", () => {
        const wrapper = mount(KsTaskIcon, {
            props: {customIcon: {icon: "https://example.com/custom.svg", monochrome: true}, onlyIcon: true},
            global: globalConfig,
        })
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.element.tagName).toBe("DIV")
        expect(icon.attributes("style")).toContain("mask-image: url(\"https://example.com/custom.svg\")")
    })

    test("resolves inner class to parent when cls contains $", () => {
        const iconsWithParent = {
            "io.kestra.plugin.core.log.Log": {flowable: false, monochrome: false, hasIcon: true},
        }
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log$SubClass", icons: iconsWithParent, onlyIcon: true},
            global: globalConfig,
        })
        // Should resolve to parent class and find the icon
        const icon = wrapper.find(".ks-task-icon__icon")
        expect(icon.attributes("src")).toBe(svgUrlFor("io.kestra.plugin.core.log.Log"))
    })

    test("lazily resolves the icon via loadIcon when it isn't in icons", async () => {
        const loadIcon = vi.fn().mockResolvedValue({flowable: false, monochrome: false, hasIcon: true})
        const wrapper = mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        // renders the fallback icon while the request is in flight
        expect(wrapper.find(".ks-task-icon__icon").attributes("src")).toBe(fallbackIcon)
        expect(loadIcon).toHaveBeenCalledWith("io.kestra.plugin.core.log.Log")

        await flushPromises()
        await wrapper.vm.$nextTick()

        expect(wrapper.find(".ks-task-icon__icon").attributes("src")).toBe(svgUrlFor("io.kestra.plugin.core.log.Log"))
    })

    test("does not call loadIcon when the icon is already provided", () => {
        const loadIcon = vi.fn().mockResolvedValue({flowable: false, monochrome: false, hasIcon: true})
        mount(KsTaskIcon, {
            props: {cls: "io.kestra.plugin.core.log.Log", icons: mockIcons, onlyIcon: true, loadIcon},
            global: globalConfig,
        })

        expect(loadIcon).not.toHaveBeenCalled()
    })
})
