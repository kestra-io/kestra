import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {createRouter, createWebHistory} from "vue-router"
import NavBarAction from "../../../../src/components/layout/NavBarAction.vue"
// The app registers the design system globally at bootstrap; unit mounts have to do it
// themselves, and this spec is specifically about what KsButton puts in the DOM.
import KsButton from "../../../../packages/design-system/src/components/Basic/KsButton/KsButton.vue"

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {path: "/", name: "home", component: {template: "<div />"}},
        {path: "/flows/new", name: "flows/create", component: {template: "<div />"}},
    ],
})

const mountAction = (props: Record<string, unknown>, attrs: Record<string, unknown> = {}) =>
    mount(NavBarAction, {
        props,
        attrs,
        global: {
            plugins: [router],
            components: {KsButton},
            // tests/unit/setup.ts stubs RouterLink with a bare `<a><slot /></a>` for the many
            // specs that mount without a router. This one installs a real router and is about
            // what actually reaches the DOM, so it needs the real component.
            stubs: {RouterLink: false},
        },
    })

describe("NavBarAction", () => {
    test("renders an anchor, not a button, when given a `to` target", async () => {
        await router.push("/")
        await router.isReady()

        const wrapper = mountAction({label: "Create", to: {name: "flows/create"}})

        // This is why an e2e locator asking for role `button` can never match it.
        expect(wrapper.get("a").attributes("href")).toBe("/flows/new")
        expect(wrapper.find("button").exists()).toBe(false)
    })

    test("renders a button when there is no `to` target", () => {
        const wrapper = mountAction({label: "Import"})

        expect(wrapper.find("button").exists()).toBe(true)
        expect(wrapper.find("a").exists()).toBe(false)
    })

    test("forwards data-test onto the rendered element so e2e can select it", async () => {
        await router.push("/")
        await router.isReady()

        // NavBarAction and KsButton both set inheritAttrs: false and re-bind $attrs, so the
        // attribute has to survive two hops before it reaches the DOM.
        const asLink = mountAction({label: "Create", to: {name: "flows/create"}}, {"data-test": "flows-create"})
        expect(asLink.get("[data-test=\"flows-create\"]").element.tagName).toBe("A")

        const asButton = mountAction({label: "Import"}, {"data-test": "flows-import"})
        expect(asButton.get("[data-test=\"flows-import\"]").element.tagName).toBe("BUTTON")
    })
})
