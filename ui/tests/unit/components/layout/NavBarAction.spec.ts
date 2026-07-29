import {describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createRouter, createMemoryHistory} from "vue-router"
import NavBarAction from "../../../../src/components/layout/NavBarAction.vue"
import {asItemKey} from "../../../../src/components/layout/navBarActionsContext"

// Mirrors what KsButton/ElButton do with `tag`: render it as the root element and pass
// everything else through, so a `to` action really goes through RouterLink here.
const KsButton = {
    name: "KsButton",
    props: ["icon", "type", "tag", "to"],
    emits: ["click"],
    template: "<component :is=\"tag ?? 'button'\" :to=\"to\" @click=\"$emit('click', $event)\"><slot/></component>",
}

const KsDropdownItem = {
    name: "KsDropdownItem",
    props: ["icon"],
    template: "<li class=\"dropdown-item\" @click=\"$emit('click', $event)\"><slot/></li>",
}

const buildRouter = () => createRouter({
    history: createMemoryHistory(),
    routes: [
        {path: "/flows", name: "flows/list", component: {template: "<div/>"}},
        {path: "/flows/new", name: "flows/create", component: {template: "<div/>"}},
    ],
})

const mountAction = async (props = {}, {asItem = false} = {}) => {
    const router = buildRouter()
    router.push({name: "flows/list"})
    await router.isReady()

    const wrapper = mount(NavBarAction, {
        props,
        global: {
            plugins: [router],
            // The suite-wide RouterLink stub renders a bare <a> with no href — this
            // component's whole contract is the href it produces, so use the real one.
            stubs: {RouterLink: false, KsButton, KsDropdownItem},
            provide: {[asItemKey as unknown as symbol]: asItem},
        },
    })

    return {wrapper, router}
}

describe("NavBarAction", () => {
    test("a `to` action renders a link pointing at that route, not at the current one", async () => {
        const {wrapper} = await mountAction({label: "Create", type: "primary", to: {name: "flows/create"}})

        const link = wrapper.get("a")
        expect(link.attributes("href")).toBe("/flows/new")
        expect(link.attributes("aria-current")).toBeUndefined()
        expect(link.text()).toBe("Create")
    })

    test("an action without `to` stays a button and emits click", async () => {
        const {wrapper} = await mountAction({label: "Import"})

        expect(wrapper.find("a").exists()).toBe(false)
        await wrapper.get("button").trigger("click")
        expect(wrapper.emitted("click")).toHaveLength(1)
    })

    test("clicking the link navigates exactly once — the link owns navigation", async () => {
        const {wrapper, router} = await mountAction({label: "Create", to: {name: "flows/create"}})
        const push = vi.spyOn(router, "push")

        await wrapper.get("a").trigger("click")

        // The link itself pushes; the component must not push a second time.
        expect(push).toHaveBeenCalledTimes(1)
        expect(wrapper.emitted("click")).toHaveLength(1)
    })

    test("a modifier click on the link leaves the current route alone", async () => {
        const {wrapper, router} = await mountAction({label: "Create", to: {name: "flows/create"}})
        const push = vi.spyOn(router, "push")

        // cmd/ctrl-click is the browser opening a new tab — navigating here as well would
        // move the tab the user meant to keep.
        await wrapper.get("a").trigger("click", {metaKey: true})

        expect(push).not.toHaveBeenCalled()
    })

    test("inside the actions dropdown, clicking the item outside the link still navigates", async () => {
        const {wrapper, router} = await mountAction({label: "Source search", to: {name: "flows/create"}}, {asItem: true})
        const push = vi.spyOn(router, "push")

        expect(wrapper.get("a").attributes("href")).toBe("/flows/new")

        await wrapper.get("li").trigger("click")
        expect(push).toHaveBeenCalledWith({name: "flows/create"})
    })
})
