import {describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createRouter, createMemoryHistory} from "vue-router"
import KestraDesignSystem from "../../../../src/index"
import KsEntityLink from "../../../../src/components/Data/KsEntityLink/KsEntityLink.vue"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [
        {path: "/", component: {template: "<div/>"}},
        {name: "namespaces/update", path: "/namespaces/edit/:id", component: {template: "<div/>"}},
        {name: "flows/update", path: "/flows/edit/:namespace/:id", component: {template: "<div/>"}},
    ],
})

// The DS test setup globally stubs RouterLink with a plain <a><slot/></a> (no
// href/navigation) so unrelated tests don't need a router. Opt out here since
// this component's contract (navigation, stopPropagation) depends on the real one.
const globalConfig = {plugins: [router, KestraDesignSystem], stubs: {RouterLink: false}}

describe("KsEntityLink", () => {
    test("renders an anchor whose accessible name is the value", () => {
        const wrapper = mount(KsEntityLink, {
            props: {
                entity: "namespace",
                value: "company.team",
                to: {name: "namespaces/update", params: {id: "company.team"}},
            },
            global: globalConfig,
        })
        const link = wrapper.find("a")
        expect(link.exists()).toBe(true)
        expect(link.text()).toBe("company.team")
    })

    test("sets the title attribute to the value", () => {
        const wrapper = mount(KsEntityLink, {
            props: {
                entity: "flow",
                value: "order_pipeline",
                to: {name: "flows/update", params: {namespace: "company.team", id: "order_pipeline"}},
            },
            global: globalConfig,
        })
        expect(wrapper.find("a").attributes("title")).toBe("order_pipeline")
    })

    test("renders the namespace icon for the namespace entity", () => {
        const wrapper = mount(KsEntityLink, {
            props: {entity: "namespace", value: "company.team", to: "/namespaces/edit/company.team"},
            global: globalConfig,
        })
        expect(wrapper.find(".folder-open-outline-icon").exists()).toBe(true)
        expect(wrapper.find(".file-tree-outline-icon").exists()).toBe(false)
    })

    test("renders the flow icon for the flow entity", () => {
        const wrapper = mount(KsEntityLink, {
            props: {entity: "flow", value: "order_pipeline", to: "/flows/edit/company.team/order_pipeline"},
            global: globalConfig,
        })
        expect(wrapper.find(".file-tree-outline-icon").exists()).toBe(true)
        expect(wrapper.find(".folder-open-outline-icon").exists()).toBe(false)
    })

    test("the icon is decorative", () => {
        const wrapper = mount(KsEntityLink, {
            props: {entity: "namespace", value: "company.team", to: "/namespaces/edit/company.team"},
            global: globalConfig,
        })
        expect(wrapper.find(".material-design-icon").attributes("aria-hidden")).toBe("true")
    })

    test("stops click propagation while still triggering navigation", async () => {
        const pushSpy = vi.spyOn(router, "push")
        let rowClicked = false

        const wrapper = mount({
            components: {KsEntityLink},
            setup() {
                return {onRowClick: () => { rowClicked = true }}
            },
            template: "<div @click=\"onRowClick\"><ks-entity-link entity=\"namespace\" value=\"company.team\" :to=\"{name: 'namespaces/update', params: {id: 'company.team'}}\" /></div>",
        }, {global: globalConfig})

        await wrapper.find("a").trigger("click")

        expect(rowClicked).toBe(false)
        expect(pushSpy).toHaveBeenCalledWith({name: "namespaces/update", params: {id: "company.team"}})
    })
})
