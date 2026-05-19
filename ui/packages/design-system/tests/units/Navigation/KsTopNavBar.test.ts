import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsTopNavBar from "../../../src/components/Navigation/KsTopNavBar/KsTopNavBar.vue"
import locales from "../../../src/components/Navigation/KsTopNavBar/KsTopNavBar.locale"

const i18n = createI18n({legacy: false, locale: "en", messages: locales})
const globalConfig = {
    plugins: [i18n, KestraDesignSystem],
    stubs: {RouterLink: {template: "<a><slot /></a>"}},
}

describe("KsTopNavBar", () => {
    test("renders nav element with the topnavbar class", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find("nav.ks-topnavbar").exists()).toBe(true)
    })

    test("renders the title via the breadcrumb title slot", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "My Flows"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("My Flows")
    })

    test("renders breadcrumb items", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {
                title: "my-flow",
                breadcrumb: [
                    {label: "Flows", link: "/flows"},
                    {label: "my-namespace", disabled: true},
                ],
            },
            global: globalConfig,
        })
        const items = wrapper.findAll(".ks-breadcrumb__item")
        expect(items.length).toBe(2)
    })

    test("renders description tooltip icon when description prop provided", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", description: "All flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".info-icon").exists()).toBe(true)
    })

    test("does not render description tooltip when no description", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".info-icon").exists()).toBe(false)
    })

    test("renders beta tag when beta prop is true", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Apps", beta: true},
            global: globalConfig,
        })
        expect(wrapper.find(".beta-tag").exists()).toBe(true)
    })

    test("star button has active class when bookmarked", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", isBookmarked: true},
            global: globalConfig,
        })
        expect(wrapper.find("button.star.active").exists()).toBe(true)
    })

    test("star button does not have active class when not bookmarked", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", isBookmarked: false},
            global: globalConfig,
        })
        expect(wrapper.find("button.star.active").exists()).toBe(false)
    })

    test("emits star-click when star button is clicked", async () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        await wrapper.find("button.star").trigger("click")
        expect(wrapper.emitted("star-click")).toBeTruthy()
    })

    test("renders sidebar toggle button when sidebarCollapsed is true", async () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", sidebarCollapsed: true},
            global: globalConfig,
        })
        const toggle = wrapper.find("button[aria-label='Toggle menu']")
        expect(toggle.exists()).toBe(true)
        await toggle.trigger("click")
        expect(wrapper.emitted("sidebar-toggle")).toBeTruthy()
    })

    test("does not render sidebar toggle button when sidebar is not collapsed", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", sidebarCollapsed: false},
            global: globalConfig,
        })
        expect(wrapper.find("button[aria-label='Toggle menu']").exists()).toBe(false)
    })

    test("renders tab select when tabs are provided", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {
                title: "Flows",
                tabs: [
                    {name: "overview", title: "Overview"},
                    {name: "logs", title: "Logs"},
                ],
                activeTab: "overview",
            },
            global: globalConfig,
        })
        expect(wrapper.find(".tab-select").exists()).toBe(true)
    })

    test("does not render tab select when no tabs", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".tab-select").exists()).toBe(false)
    })

    test("renders dock-toggle button when showDockToggle is true", async () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", showDockToggle: true},
            global: globalConfig,
        })
        const dock = wrapper.find("button.dock-toggle")
        expect(dock.exists()).toBe(true)
        await dock.trigger("click")
        expect(wrapper.emitted("dock-toggle")).toBeTruthy()
    })

    test("dock-toggle has is-open class when isDockOpen is true", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", showDockToggle: true, isDockOpen: true},
            global: globalConfig,
        })
        expect(wrapper.find("button.dock-toggle.is-open").exists()).toBe(true)
    })

    test("does not render dock-toggle when showDockToggle is false", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", showDockToggle: false},
            global: globalConfig,
        })
        expect(wrapper.find("button.dock-toggle").exists()).toBe(false)
    })

    test("renders custom title slot", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            slots: {title: "<span class=\"custom-title\">Custom Title</span>"},
            global: globalConfig,
        })
        expect(wrapper.find(".custom-title").exists()).toBe(true)
    })

    test("renders search slot", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            slots: {search: "<input class=\"search-input\" />"},
            global: globalConfig,
        })
        expect(wrapper.find(".search-input").exists()).toBe(true)
    })

    test("renders actions slot", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            slots: {actions: "<button class=\"delete-btn\">Delete</button>"},
            global: globalConfig,
        })
        expect(wrapper.find(".delete-btn").exists()).toBe(true)
    })

    test("description slot is hidden via v-show when showDescription is false", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", showDescription: false},
            slots: {description: "<span class=\"desc\">hidden</span>"},
            global: globalConfig,
        })
        const descriptionEl = wrapper.find(".description")
        expect(descriptionEl.exists()).toBe(true)
        expect((descriptionEl.element as HTMLElement).style.display).toBe("none")
    })

    test("description slot is visible when showDescription is true", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", showDescription: true},
            slots: {description: "<span class=\"desc\">shown</span>"},
            global: globalConfig,
        })
        const descriptionEl = wrapper.find(".description")
        expect(descriptionEl.exists()).toBe(true)
        expect((descriptionEl.element as HTMLElement).style.display).not.toBe("none")
    })
})
