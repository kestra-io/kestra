import {markRaw} from "vue"
import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsSideBar from "../../../src/components/Navigation/KsSideBar/KsSideBar.vue"
import KsSideBarItem from "../../../src/components/Navigation/KsSideBar/KsSideBarItem.vue"
import KsSideBarSection from "../../../src/components/Navigation/KsSideBar/KsSideBarSection.vue"
import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSideBar", () => {
    test("renders nav with the default aria-label", () => {
        const wrapper = mount(KsSideBar, {global: globalConfig})
        const nav = wrapper.find("nav.ks-sidebar")
        expect(nav.exists()).toBe(true)
        expect(nav.attributes("aria-label")).toBe("Sidebar")
    })

    test("honors a custom aria-label", () => {
        const wrapper = mount(KsSideBar, {
            global: globalConfig,
            props: {ariaLabel: "Main navigation"},
        })
        expect(wrapper.find("nav.ks-sidebar").attributes("aria-label")).toBe("Main navigation")
    })

    test("renders header, default, and footer slot content", () => {
        const wrapper = mount(KsSideBar, {
            global: globalConfig,
            slots: {
                header: "<div class='hdr'>H</div>",
                default: "<div class='body'>B</div>",
                footer: "<div class='ftr'>F</div>",
            },
        })
        expect(wrapper.find(".ks-sidebar__header .hdr").exists()).toBe(true)
        expect(wrapper.find(".ks-sidebar__body .body").exists()).toBe(true)
        expect(wrapper.find(".ks-sidebar__footer .ftr").exists()).toBe(true)
    })

    test("omits header/footer wrappers when their slots are empty", () => {
        const wrapper = mount(KsSideBar, {global: globalConfig})
        expect(wrapper.find(".ks-sidebar__header").exists()).toBe(false)
        expect(wrapper.find(".ks-sidebar__footer").exists()).toBe(false)
    })
})

describe("KsSideBarSection", () => {
    test("renders the title and child content", () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace"},
            slots: {default: "<a class='child'>Flows</a>"},
        })
        expect(wrapper.find(".ks-sidebar-section__title").text()).toBe("Workspace")
        expect(wrapper.find(".child").exists()).toBe(true)
    })

    test("omits the title element when no title is provided", () => {
        const wrapper = mount(KsSideBarSection, {global: globalConfig})
        expect(wrapper.find(".ks-sidebar-section__title").exists()).toBe(false)
    })

    test("non-collapsible title renders as a div with no chevron", () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace"},
        })
        expect(wrapper.find("button.ks-sidebar-section__title").exists()).toBe(false)
        expect(wrapper.find(".ks-sidebar-section__chevron").exists()).toBe(false)
    })

    test("collapsible title renders as a button with a chevron", () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace", collapsible: true},
        })
        const btn = wrapper.find("button.ks-sidebar-section__title")
        expect(btn.exists()).toBe(true)
        expect(btn.attributes("aria-expanded")).toBe("true")
        expect(wrapper.find(".ks-sidebar-section__chevron").exists()).toBe(true)
    })

    test("clicking the collapsible title toggles content and emits toggle", async () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace", collapsible: true},
            slots: {default: "<a class='child'>Flows</a>"},
        })
        const btn = wrapper.find("button.ks-sidebar-section__title")

        // Initially expanded
        expect(wrapper.find(".ks-sidebar-section__body").classes()).toContain("is-open")
        expect(wrapper.find(".ks-sidebar-section__body-inner").attributes("inert")).toBeUndefined()
        expect(btn.attributes("aria-expanded")).toBe("true")

        await btn.trigger("click")
        expect(wrapper.find(".ks-sidebar-section__body").classes()).not.toContain("is-open")
        expect(wrapper.find(".ks-sidebar-section__body-inner").attributes("inert")).toBeDefined()
        expect(btn.attributes("aria-expanded")).toBe("false")
        expect(wrapper.emitted("toggle")?.[0]).toEqual([true])

        await btn.trigger("click")
        expect(wrapper.find(".ks-sidebar-section__body").classes()).toContain("is-open")
        expect(wrapper.emitted("toggle")?.[1]).toEqual([false])
    })

    test("respects defaultCollapsed", () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace", collapsible: true, defaultCollapsed: true},
            slots: {default: "<a class='child'>Flows</a>"},
        })
        expect(wrapper.find(".ks-sidebar-section__body").classes()).not.toContain("is-open")
        expect(wrapper.find(".ks-sidebar-section__body-inner").attributes("inert")).toBeDefined()
        expect(wrapper.find("button.ks-sidebar-section__title").attributes("aria-expanded")).toBe("false")
    })

    test("controlled mode: collapsed prop drives the displayed state", async () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace", collapsible: true, collapsed: true},
            slots: {default: "<a class='child'>Flows</a>"},
        })
        expect(wrapper.find(".ks-sidebar-section__body").classes()).not.toContain("is-open")
        expect(wrapper.find(".ks-sidebar-section__body-inner").attributes("inert")).toBeDefined()
        expect(wrapper.find("button.ks-sidebar-section__title").attributes("aria-expanded")).toBe("false")

        await wrapper.setProps({collapsed: false})
        expect(wrapper.find(".ks-sidebar-section__body").classes()).toContain("is-open")
        expect(wrapper.find(".ks-sidebar-section__body-inner").attributes("inert")).toBeUndefined()
        expect(wrapper.find("button.ks-sidebar-section__title").attributes("aria-expanded")).toBe("true")
    })

    test("controlled mode: clicking emits update:collapsed without mutating internal state", async () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace", collapsible: true, collapsed: false},
            slots: {default: "<a class='child'>Flows</a>"},
        })

        await wrapper.find("button.ks-sidebar-section__title").trigger("click")

        expect(wrapper.emitted("update:collapsed")?.[0]).toEqual([true])
        expect(wrapper.emitted("toggle")?.[0]).toEqual([true])
        expect(wrapper.find(".ks-sidebar-section__body").classes()).toContain("is-open")
        expect(wrapper.find("button.ks-sidebar-section__title").attributes("aria-expanded")).toBe("true")
    })

    test("controlled mode: defaultCollapsed change does NOT override the controlled value", async () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace", collapsible: true, collapsed: true, defaultCollapsed: true},
            slots: {default: "<a class='child'>Flows</a>"},
        })
        await wrapper.setProps({defaultCollapsed: false})
        expect(wrapper.find(".ks-sidebar-section__body").classes()).not.toContain("is-open")
        expect(wrapper.find(".ks-sidebar-section__body-inner").attributes("inert")).toBeDefined()
        expect(wrapper.find("button.ks-sidebar-section__title").attributes("aria-expanded")).toBe("false")
    })

    test("renders the suffix slot inside the collapsible title", () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace", collapsible: true},
            slots: {suffix: "<span class='dot'>•</span>"},
        })
        expect(wrapper.find("button.ks-sidebar-section__title .dot").exists()).toBe(true)
    })

    test("renders the suffix slot inside the non-collapsible title", () => {
        const wrapper = mount(KsSideBarSection, {
            global: globalConfig,
            props: {title: "Workspace"},
            slots: {suffix: "<span class='dot'>•</span>"},
        })
        expect(wrapper.find(".ks-sidebar-section__title .dot").exists()).toBe(true)
        expect(wrapper.find(".ks-sidebar-section__title-text").text()).toBe("Workspace")
    })
})

describe("KsSideBarItem", () => {
    test("renders an anchor with the title and href", () => {
        const wrapper = mount(KsSideBarItem, {
            global: globalConfig,
            props: {title: "Flows", href: "/flows"},
        })
        const a = wrapper.find("a.ks-sidebar-item")
        expect(a.exists()).toBe(true)
        expect(a.attributes("href")).toBe("/flows")
        expect(wrapper.find(".ks-sidebar-item__title").text()).toBe("Flows")
    })

    test("applies the active class and aria-current when active", () => {
        const wrapper = mount(KsSideBarItem, {
            global: globalConfig,
            props: {title: "Flows", href: "/flows", active: true},
        })
        const a = wrapper.find("a.ks-sidebar-item")
        expect(a.classes()).toContain("is-active")
        expect(a.attributes("aria-current")).toBe("page")
    })

    test("renders the lock icon when locked", () => {
        const wrapper = mount(KsSideBarItem, {
            global: globalConfig,
            props: {title: "Secrets", locked: true},
        })
        expect(wrapper.find(".ks-sidebar-item__lock").exists()).toBe(true)
        expect(wrapper.find("a.ks-sidebar-item").classes()).toContain("is-locked")
    })

    test("renders the icon component when provided", () => {
        const wrapper = mount(KsSideBarItem, {
            global: globalConfig,
            props: {title: "Flows", icon: markRaw(FileTreeOutline)},
        })
        expect(wrapper.find(".ks-sidebar-item__icon").exists()).toBe(true)
    })

    test("emits click when the anchor is clicked", async () => {
        const wrapper = mount(KsSideBarItem, {
            global: globalConfig,
            props: {title: "Flows", href: "/flows"},
        })
        await wrapper.find("a.ks-sidebar-item").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
        expect(wrapper.emitted("click")?.length).toBe(1)
    })

    test("renders as a span and prevents click when disabled", async () => {
        const wrapper = mount(KsSideBarItem, {
            global: globalConfig,
            props: {title: "Flows", href: "/flows", disabled: true},
        })
        const root = wrapper.find(".ks-sidebar-item")
        expect(root.element.tagName).toBe("SPAN")
        expect(root.classes()).toContain("is-disabled")
        expect(root.attributes("aria-disabled")).toBe("true")
        expect(root.attributes("href")).toBeUndefined()
        await root.trigger("click")
        expect(wrapper.emitted("click")).toBeUndefined()
    })

    test("renders default slot in place of the title when provided", () => {
        const wrapper = mount(KsSideBarItem, {
            global: globalConfig,
            props: {title: "Fallback"},
            slots: {default: "<span class='custom'>Custom label</span>"},
        })
        expect(wrapper.find(".custom").exists()).toBe(true)
        expect(wrapper.find(".ks-sidebar-item__title").text()).toBe("Custom label")
    })
})
