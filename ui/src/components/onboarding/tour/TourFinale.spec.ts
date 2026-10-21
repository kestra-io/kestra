import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {createMemoryHistory, createRouter, type Router} from "vue-router"
import {defineComponent, h, nextTick, ref} from "vue"

import {i18nMount} from "../../../../tests/unit/i18nMount"
import TourFinale from "./TourFinale.vue"

const Page = defineComponent({setup: () => () => h("div")})

const createTestRouter = () => createRouter({
    history: createMemoryHistory(),
    routes: [
        {path: "/executions", name: "executions/list", component: Page},
        {path: "/blueprints/:kind/:tab", name: "blueprints", component: Page},
    ],
})

const Host = defineComponent({
    components: {TourFinale},
    setup() {
        const open = ref(true)
        return {open}
    },
    template: "<TourFinale v-model=\"open\" :lockScroll=\"false\" /><span data-test=\"host-open\">{{ open }}</span>",
})

const resourceLinks = () => Array.from(document.body.querySelectorAll<HTMLAnchorElement>(".onboarding-resource-item"))
const hostOpen = () => document.body.querySelector("[data-test=host-open]")?.textContent

describe("TourFinale", () => {
    let router: Router
    let wrapper: VueWrapper | undefined

    beforeEach(async () => {
        router = createTestRouter()
        await router.push("/executions")
        await router.isReady()
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    const mountFinale = async () => {
        wrapper = i18nMount(Host, {
            attachTo: document.body,
            global: {plugins: [router], stubs: {RouterLink: false}},
        })
        await flushPromises()
    }

    it("should close the dialog and navigate when the in-app Keep going link is followed", async () => {
        // Given
        await mountFinale()
        expect(hostOpen()).toBe("true")

        // When
        resourceLinks()[0].click()
        await flushPromises()
        await nextTick()

        // Then
        expect(hostOpen()).toBe("false")
        expect(router.currentRoute.value.name).toBe("blueprints")
    })

    it("should keep the dialog open when an external Keep going link is followed", async () => {
        // Given
        await mountFinale()
        const external = resourceLinks().filter((link) => link.getAttribute("target") === "_blank")
        expect(external.length).toBeGreaterThan(0)

        // When
        external[0].addEventListener("click", (event) => event.preventDefault())
        external[0].click()
        await flushPromises()

        // Then
        expect(hostOpen()).toBe("true")
        expect(router.currentRoute.value.name).toBe("executions/list")
    })

    it("should render every Keep going link as a real anchor with a destination", async () => {
        // Given
        await mountFinale()

        // When
        const links = resourceLinks()

        // Then
        expect(links.length).toBeGreaterThan(1)
        links.forEach((link) => expect(link.getAttribute("href"), link.textContent ?? "").toBeTruthy())
        expect(links[0].getAttribute("href")).toBe("/blueprints/flow/community")
    })
})
