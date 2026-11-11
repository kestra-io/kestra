import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {createPinia, setActivePinia, type Pinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {i18nMount} from "../../../tests/unit/i18nMount"
import "../../utils/global"
import {useDocStore} from "../../stores/doc"
import ContextDocsMenu from "./ContextDocsMenu.vue"

describe("ContextDocsMenu", () => {
    let pinia: Pinia
    let wrapper: VueWrapper | undefined

    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
        vi.spyOn(useDocStore(), "children").mockResolvedValue({"docs/quickstart": {title: "Quickstart"}})
    })
    afterEach(() => wrapper?.unmount())

    async function openMenu() {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [
                {name: "docs/view", path: "/docs/:path(.*)", component: {render: () => null}},
                {path: "/:pathMatch(.*)*", component: {render: () => null}},
            ],
        })
        wrapper = i18nMount(ContextDocsMenu, {global: {plugins: [pinia, router], stubs: {RouterLink: false}}, attachTo: document.body})
        await wrapper.get("button").trigger("click")
        await flushPromises()
    }

    const isOpen = () => wrapper?.get("button").attributes("aria-expanded") === "true"

    it("closes when the user clicks outside it, but not inside it", async () => {
        await openMenu()
        expect(isOpen()).toBe(true)

        await wrapper!.get("ul").trigger("click")
        await flushPromises()
        expect(isOpen()).toBe(true)

        document.body.click()
        await flushPromises()
        expect(isOpen()).toBe(false)
    })

    it("closes on Escape and gives focus back to the button when it was in the menu", async () => {
        await openMenu()
        const link = wrapper!.get("a")
        expect(link.text()).toBe("Quickstart")
        ;(link.element as HTMLElement).focus()

        window.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
        await flushPromises()

        expect(isOpen()).toBe(false)
        expect(document.activeElement).toBe(wrapper!.get("button").element)
    })
})
