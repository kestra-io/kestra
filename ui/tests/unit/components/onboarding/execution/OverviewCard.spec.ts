import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {createRouter, createWebHistory} from "vue-router"
import OverviewCard from "../../../../../src/components/onboarding/execution/OverviewCard.vue"

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {path: "/", name: "home", component: {template: "<div />"}},
        {path: "/blueprints", name: "blueprints", component: {template: "<div />"}},
    ],
})

const mountCard = (props: Record<string, unknown> = {}) =>
    mount(OverviewCard, {
        props: {title: "Card title", description: "Card description", ...props},
        global: {
            plugins: [router],
            stubs: {
                KsIcon: true,
                // tests/unit/setup.ts stubs RouterLink globally for router-less mounts; this spec
                // is about the href the real RouterLink puts in the DOM, so it needs the real one.
                RouterLink: false,
            },
        },
    })

describe("OverviewCard", () => {
    test("keeps the router-resolved href on an internal `to` card", async () => {
        await router.push("/")
        await router.isReady()

        const wrapper = mountCard({to: {name: "blueprints"}})

        // Regression for https://github.com/kestra-io/kestra/issues/18148: passing the unset
        // `link` props as `href/target/rel: undefined` fell through onto RouterLink's anchor and
        // wiped its href, so the card lost the native link cursor while staying clickable.
        const anchor = wrapper.get("a")
        expect(anchor.attributes("href")).toBe("/blueprints")
        expect(anchor.attributes("target")).toBeUndefined()
        expect(anchor.attributes("rel")).toBeUndefined()
    })

    test("renders an external `link` card as an anchor opening safely in a new tab", () => {
        const wrapper = mountCard({link: "https://kestra.io/slack"})

        const anchor = wrapper.get("a")
        expect(anchor.attributes("href")).toBe("https://kestra.io/slack")
        expect(anchor.attributes("target")).toBe("_blank")
        expect(anchor.attributes("rel")).toContain("noopener")
    })

    test("renders a plain non-interactive div when given neither `to` nor `link`", () => {
        const wrapper = mountCard()

        expect(wrapper.find("a").exists()).toBe(false)
        expect(wrapper.get("div.card").attributes("href")).toBeUndefined()
    })
})
