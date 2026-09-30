import {afterEach, describe, test, expect, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createRouter, createMemoryHistory} from "vue-router"
import KsBreadcrumb from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumb.vue"
import KsBreadcrumbMenu from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumbMenu.vue"
import locales from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumb.locale"
import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "../../../src/components/Navigation/KsBreadcrumb/types"
import {i18nMount} from "../i18nMount"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/:path(.*)*", component: {template: "<div/>"}}],
})

const globalConfig = {plugins: [router]}

const menuEntries = () => Array.from(document.body.querySelectorAll("[data-testid=\"breadcrumb-entry\"]"))
const labels = () => menuEntries().map((entry) => entry.textContent?.trim())

describe("KsBreadcrumb", () => {
    test("renders the root element", () => {
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-breadcrumb").exists()).toBe(true)
    })

    test("does not render the leading section by default", () => {
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-breadcrumb__leading").exists()).toBe(false)
    })

    test("renders the leading monogram wrapped in a RouterLink when showLeading is true", () => {
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {title: "Flows", showLeading: true},
            global: globalConfig,
        })
        const leading = wrapper.find(".ks-breadcrumb__leading")
        expect(leading.exists()).toBe(true)
        // RouterLink renders as <a>
        expect(leading.element.tagName).toBe("A")
        expect(wrapper.find(".ks-breadcrumb__monogram").exists()).toBe(true)
    })

    test("renders the title", () => {
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {title: "Preferences"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-breadcrumb__current").text()).toBe("Preferences")
    })

    test("renders title slot over title prop", () => {
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {title: "From prop"},
            slots: {title: "From slot"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-breadcrumb__current").text()).toBe("From slot")
    })

    test("renders items as links", () => {
        const items: KsBreadcrumbItem[] = [
            {label: "Admin", onClick: () => {}},
            {label: "IAM", onClick: () => {}},
        ]
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {items, title: "Members"},
            global: globalConfig,
        })
        expect(wrapper.findAll(".ks-breadcrumb__item").length).toBe(2)
    })

    test("invokes onClick when an item is clicked", async () => {
        let clicked = false
        const items: KsBreadcrumbItem[] = [
            {label: "Admin", onClick: () => { clicked = true }},
        ]
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {items, title: "Members"},
            global: globalConfig,
        })
        await wrapper.find(".ks-breadcrumb__link").trigger("click")
        expect(clicked).toBe(true)
    })

    test("heads a level's menu with the scope of the item above it", async () => {
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {
                items: [
                    {label: "Flows", scope: "namespaces", onClick: () => {}},
                    {label: "company", onClick: () => {}, siblings: vi.fn().mockResolvedValue([{label: "company", current: true}, {label: "system"}])},
                ],
                title: "etl",
            },
            global: globalConfig,
            attachTo: document.body,
        })
        await flushPromises()

        await wrapper.findAll("[data-testid=\"breadcrumb-segment\"]")[0].trigger("mouseenter")
        await new Promise((resolve) => setTimeout(resolve, 200))
        await flushPromises()

        expect(document.body.querySelector(".heading")?.textContent?.trim()).toBe("In namespaces")
        wrapper.unmount()
        document.body.innerHTML = ""
    })

    test("collapses middle items when there are 4+", () => {
        const items: KsBreadcrumbItem[] = [
            {label: "One", onClick: () => {}},
            {label: "Two", onClick: () => {}},
            {label: "Three", onClick: () => {}},
            {label: "Four", onClick: () => {}},
        ]
        const wrapper = i18nMount(KsBreadcrumb, {
            locales,
            props: {items, title: "Five"},
            global: globalConfig,
        })
        // First, ellipsis, last item — three breadcrumb items rendered.
        const rendered = wrapper.findAll(".ks-breadcrumb__item")
        expect(rendered.length).toBe(3)
        expect(rendered[1].text()).toBe("...")
    })
})

describe("KsBreadcrumbMenu", () => {
    afterEach(() => {
        document.body.innerHTML = ""
    })

    // Mounts the menu and hovers its segment, which opens the popover after its show delay.
    const mountMenu = async (load: KsBreadcrumbLoader) => {
        const wrapper = i18nMount(KsBreadcrumbMenu, {
            locales,
            props: {load, ariaLabel: "Other entries"},
            global: globalConfig,
            attachTo: document.body,
        })
        await flushPromises()
        const segment = wrapper.find("[data-testid=\"breadcrumb-segment\"]")
        if (segment.exists()) {
            await segment.trigger("mouseenter")
            await new Promise((resolve) => setTimeout(resolve, 200))
            await flushPromises()
        }
        return wrapper
    }

    test("lists the entries fetched for the level", async () => {
        const wrapper = await mountMenu(vi.fn().mockResolvedValue([{label: "team", link: "/team"}, {label: "sales", link: "/sales"}]))

        expect(wrapper.find("[data-testid=\"breadcrumb-menu-trigger\"]").exists()).toBe(true)
        expect(labels()).toEqual(["team", "sales"])
        wrapper.unmount()
    })

    test("renders nothing but its slot when the level has nothing to offer", async () => {
        const wrapper = await mountMenu(vi.fn().mockResolvedValue([]))

        expect(wrapper.find("[data-testid=\"breadcrumb-menu-trigger\"]").exists()).toBe(false)
        wrapper.unmount()
    })

    test("flies out the content of an entry once it is hovered", async () => {
        const children = vi.fn().mockResolvedValue([{label: "invoicing", link: "/finance.invoicing"}])
        const wrapper = await mountMenu(vi.fn().mockResolvedValue([{label: "finance", link: "/finance", children}]))
        expect(children).not.toHaveBeenCalled()

        menuEntries()[0].dispatchEvent(new MouseEvent("mouseenter"))
        await flushPromises()

        expect(children).toHaveBeenCalledTimes(1)
        expect(labels()).toEqual(["finance", "invoicing"])
        wrapper.unmount()
    })

    test("links an entry to its target and closes once it is chosen", async () => {
        const wrapper = await mountMenu(vi.fn().mockResolvedValue([{label: "weekly-report", link: "/weekly-report"}]))
        const entry = wrapper.findAllComponents({name: "RouterLink"}).find((link) => link.text() === "weekly-report")
        expect(entry?.props("to")).toBe("/weekly-report")

        await entry?.trigger("click")
        await flushPromises()

        expect(wrapper.find("[data-testid=\"breadcrumb-menu-trigger\"]").attributes("aria-expanded")).toBe("false")
        wrapper.unmount()
    })
})
