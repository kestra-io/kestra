import {describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createRouter, createWebHistory} from "vue-router"
import Tabs from "../../../src/components/Tabs.vue"

// Statically imported by Tabs.vue but only rendered for the blueprint modal; stub it
// out so the spec doesn't pull the whole blueprints dependency graph.
vi.mock("override/components/flows/blueprints/BlueprintDetail.vue", () => ({
    default: {name: "BlueprintDetail", template: "<div />"},
}))

const router = createRouter({
    history: createWebHistory(),
    routes: [{path: "/", name: "home", component: {template: "<div />"}}],
})

const mountTabs = (props: InstanceType<typeof Tabs>["$props"]) =>
    mount(Tabs, {
        props,
        global: {
            plugins: [router, createPinia()],
            // The app registers the design system globally at bootstrap; this spec is
            // about whether the content <section> renders, so the bar can be stubbed.
            stubs: {
                KsTabs: {template: "<div class=\"ks-tabs-bar\"><slot /></div>"},
                KsTabPane: true,
                EnterpriseBadge: true,
            },
        },
    })

describe("Tabs", () => {
    test("renders no content section when the embedded active tab has no component — regression for kestra-ee#9695", async () => {
        await router.push("/")
        await router.isReady()

        // Decorative tab bar, as used by EE detail pages (Group.vue / Role.vue via
        // IAMTabs): tabs mirror routed pages, so they carry no `component`. An empty
        // full-container section would flex-grow and push the page content down.
        const wrapper = mountTabs({
            tabs: [{name: "groups", title: "Groups", fullContainer: true}],
            embedActiveTab: "groups",
        })

        expect(wrapper.find("section").exists()).toBe(false)
    })

    test("renders the content section when the embedded active tab has a component", async () => {
        await router.push("/")
        await router.isReady()

        const wrapper = mountTabs({
            tabs: [{name: "details", title: "Details", component: {template: "<p data-test=\"tab-body\">body</p>"}}],
            embedActiveTab: "details",
        })

        expect(wrapper.get("section [data-test=\"tab-body\"]").text()).toBe("body")
    })
})
