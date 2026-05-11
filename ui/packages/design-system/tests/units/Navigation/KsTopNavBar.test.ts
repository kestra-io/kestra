import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTopNavBar from "../../../src/components/Navigation/KsTopNavBar/KsTopNavBar.vue"

const globalConfig = {
    plugins: [KestraDesignSystem],
    stubs: {RouterLink: {template: "<a><slot /></a>"}},
}

describe("KsTopNavBar", () => {
    test("renders nav element with correct class", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find("nav.ks-topnavbar").exists()).toBe(true)
    })

    test("renders title", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "My Flows"},
            global: globalConfig,
        })
        expect(wrapper.find("h1").text()).toContain("My Flows")
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
        const items = wrapper.findAll(".kel-breadcrumb__item")
        expect(items.length).toBe(2)
    })

    test("disabled breadcrumb item renders as anchor without link", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {
                title: "my-flow",
                breadcrumb: [{label: "Namespace", disabled: true}],
            },
            global: globalConfig,
        })
        expect(wrapper.find(".pe-none").exists()).toBe(true)
        expect(wrapper.find(".pe-none a").exists()).toBe(true)
    })

    test("renders description tooltip icon when description prop provided", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", description: "All flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".material-design-icon").exists()).toBe(true)
    })

    test("renders longDescription in description area", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", longDescription: "Detailed description text"},
            global: globalConfig,
        })
        expect(wrapper.find(".description").text()).toContain("Detailed description text")
    })

    test("renders beta badge when beta prop is true", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Apps", beta: true},
            global: globalConfig,
        })
        expect(wrapper.find(".beta-badge").exists()).toBe(true)
    })

    test("star button has active class when bookmarked", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", isBookmarked: true},
            global: globalConfig,
        })
        expect(wrapper.find("button.icon.active").exists()).toBe(true)
    })

    test("star button does not have active class when not bookmarked", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows", isBookmarked: false},
            global: globalConfig,
        })
        expect(wrapper.find("button.icon.active").exists()).toBe(false)
    })

    test("emits star-click event when star button is clicked", async () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        await wrapper.find("button.icon").trigger("click")
        expect(wrapper.emitted("star-click")).toBeTruthy()
    })

    test("renders sidebar-toggle slot", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            slots: {"sidebar-toggle": "<button class=\"sidebar-btn\">Toggle</button>"},
            global: globalConfig,
        })
        expect(wrapper.find(".sidebar-btn").exists()).toBe(true)
    })

    test("renders actions slot", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            slots: {"actions": "<span class=\"custom-action\">Action</span>"},
            global: globalConfig,
        })
        expect(wrapper.find(".custom-action").exists()).toBe(true)
    })

    test("renders custom title slot", () => {
        const wrapper = mount(KsTopNavBar, {
            props: {title: "Flows"},
            slots: {title: "<span class=\"custom-title\">Custom Title</span>"},
            global: globalConfig,
        })
        expect(wrapper.find(".custom-title").exists()).toBe(true)
    })

    test("renders search slot in search area", () => {
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
})
