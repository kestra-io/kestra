import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createRouter, createMemoryHistory} from "vue-router"
import KestraDesignSystem from "../../../src/index"
import KsBreadcrumb from "../../../src/components/Navigation/KsBreadcrumb/KsBreadcrumb.vue"
import type {KsBreadcrumbItem} from "../../../src/components/Navigation/KsBreadcrumb/types"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", component: {template: "<div/>"}}],
})

const globalConfig = {plugins: [router, KestraDesignSystem]}

describe("KsBreadcrumb", () => {
    test("renders the root element", () => {
        const wrapper = mount(KsBreadcrumb, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-breadcrumb").exists()).toBe(true)
    })

    test("does not render the leading section by default", () => {
        const wrapper = mount(KsBreadcrumb, {
            props: {title: "Flows"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-breadcrumb__leading").exists()).toBe(false)
    })

    test("renders the leading monogram wrapped in a RouterLink when showLeading is true", () => {
        const wrapper = mount(KsBreadcrumb, {
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
        const wrapper = mount(KsBreadcrumb, {
            props: {title: "Preferences"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-breadcrumb__current").text()).toBe("Preferences")
    })

    test("renders title slot over title prop", () => {
        const wrapper = mount(KsBreadcrumb, {
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
        const wrapper = mount(KsBreadcrumb, {
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
        const wrapper = mount(KsBreadcrumb, {
            props: {items, title: "Members"},
            global: globalConfig,
        })
        await wrapper.find(".ks-breadcrumb__link").trigger("click")
        expect(clicked).toBe(true)
    })

    test("collapses middle items when there are 4+", () => {
        const items: KsBreadcrumbItem[] = [
            {label: "One", onClick: () => {}},
            {label: "Two", onClick: () => {}},
            {label: "Three", onClick: () => {}},
            {label: "Four", onClick: () => {}},
        ]
        const wrapper = mount(KsBreadcrumb, {
            props: {items, title: "Five"},
            global: globalConfig,
        })
        // First, ellipsis dropdown, last item — three breadcrumb items rendered.
        expect(wrapper.findAll(".ks-breadcrumb__item").length).toBe(3)
        expect(wrapper.find(".ks-breadcrumb__ellipsis").exists()).toBe(true)
    })
})
