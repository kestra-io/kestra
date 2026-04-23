import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createRouter, createMemoryHistory} from "vue-router"
import KestraDesignSystem from "../../../src/index"
import {KsRouterTab} from "../../../src"
import type {RouterTab} from "../../../src"
import {defineComponent, markRaw} from "vue"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/:tab?", name: "demo", component: {template: "<div/>"}}],
})

const globalConfig = {plugins: [KestraDesignSystem, router]}

const baseTabs: RouterTab[] = [
    {name: "overview", title: "Overview"},
    {name: "logs", title: "Logs"},
    {name: "metrics", title: "Metrics"},
]

describe("KsRouterTab", () => {
    test("renders tab items for each non-hidden tab", () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "overview"},
            global: globalConfig,
        })
        const items = wrapper.findAll(".kel-tabs__item")
        expect(items.length).toBe(3)
    })

    test("does not render hidden tabs", () => {
        const tabs: RouterTab[] = [
            {name: "a", title: "A"},
            {name: "b", title: "B", hidden: true},
            {name: "c", title: "C"},
        ]
        const wrapper = mount(KsRouterTab, {
            props: {tabs, embedActiveTab: "a"},
            global: globalConfig,
        })
        expect(wrapper.findAll(".kel-tabs__item").length).toBe(2)
    })

    test("active tab matches embedActiveTab", async () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "logs"},
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()
        const active = wrapper.find(".kel-tabs__item.is-active")
        expect(active.text()).toContain("Logs")
    })

    test("disabled tab has is-disabled class", () => {
        const tabs: RouterTab[] = [
            {name: "a", title: "A"},
            {name: "b", title: "B", disabled: true},
        ]
        const wrapper = mount(KsRouterTab, {
            props: {tabs, embedActiveTab: "a"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tabs__item.is-disabled").exists()).toBe(true)
    })

    test("emits changed event with the clicked tab in embedded mode", async () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "overview"},
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()
        // In embedded mode the label renders as <a>, find the second one (Logs)
        const anchors = wrapper.findAll(".kel-tabs__item a")
        await anchors[1].trigger("click")
        expect(wrapper.emitted("changed")).toBeTruthy()
        expect((wrapper.emitted("changed")![0][0] as RouterTab).name).toBe("logs")
    })

    test("renders count badge when count is defined", () => {
        const tabs: RouterTab[] = [
            {name: "a", title: "A", count: 5},
            {name: "b", title: "B"},
        ]
        const wrapper = mount(KsRouterTab, {
            props: {tabs, embedActiveTab: "a"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-router-tab__badge").exists()).toBe(true)
        expect(wrapper.find(".kel-badge__content").text()).toBe("5")
    })

    test("does not render count badge when count is undefined", () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "overview"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-router-tab__badge").exists()).toBe(false)
    })

    test("renders count badge when count is 0", () => {
        const tabs: RouterTab[] = [{name: "a", title: "A", count: 0}]
        const wrapper = mount(KsRouterTab, {
            props: {tabs, embedActiveTab: "a"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-router-tab__badge").exists()).toBe(true)
    })

    test("content section is not rendered when no component or content slot", () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "overview"},
            global: globalConfig,
        })
        expect(wrapper.find("section").exists()).toBe(false)
    })

    test("renders content slot with activeTab scoped prop", () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "logs"},
            slots: {
                content: "<template #content=\"{activeTab}\"><div class=\"slot-content\">{{ activeTab.title }}</div></template>",
            },
            global: globalConfig,
        })
        expect(wrapper.find(".slot-content").exists()).toBe(true)
        expect(wrapper.find(".slot-content").text()).toBe("Logs")
    })

    test("renders tab-label slot with tab scoped prop", () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "overview"},
            slots: {
                "tab-label": "<template #tab-label=\"{tab}\"><span class=\"custom-label\">{{ tab.name }}-custom</span></template>",
            },
            global: globalConfig,
        })
        const customLabels = wrapper.findAll(".custom-label")
        expect(customLabels.length).toBe(3)
        expect(customLabels[0].text()).toBe("overview-custom")
    })

    test("renders activeTab.component inside the section", () => {
        const DummyComponent = markRaw(defineComponent({
            template: "<div class=\"dummy-component\">Dummy</div>",
        }))
        const tabs: RouterTab[] = [
            {name: "a", title: "A", component: DummyComponent},
        ]
        const wrapper = mount(KsRouterTab, {
            props: {tabs, embedActiveTab: "a"},
            global: globalConfig,
        })
        expect(wrapper.find("section").exists()).toBe(true)
        expect(wrapper.find(".dummy-component").exists()).toBe(true)
    })

    test("section has maximized class when activeTab.maximized is true", () => {
        const tabs: RouterTab[] = [
            {name: "a", title: "A", component: markRaw(defineComponent({template: "<div/>"})), maximized: true},
        ]
        const wrapper = mount(KsRouterTab, {
            props: {tabs, embedActiveTab: "a"},
            global: globalConfig,
        })
        expect(wrapper.find("section.maximized").exists()).toBe(true)
    })

    test("section has no-overflow class when activeTab.noOverflow is true", () => {
        const tabs: RouterTab[] = [
            {name: "a", title: "A", component: markRaw(defineComponent({template: "<div/>"})), noOverflow: true},
        ]
        const wrapper = mount(KsRouterTab, {
            props: {tabs, embedActiveTab: "a"},
            global: globalConfig,
        })
        expect(wrapper.find("section.no-overflow").exists()).toBe(true)
    })

    test("fallback to first tab when embedActiveTab does not match any tab", async () => {
        const wrapper = mount(KsRouterTab, {
            props: {tabs: baseTabs, embedActiveTab: "nonexistent"},
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()
        const active = wrapper.find(".kel-tabs__item.is-active")
        expect(active.text()).toContain("Overview")
    })
})
