import {describe, it, expect, vi, beforeEach} from "vitest"
import {h} from "vue"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

const {push} = vi.hoisted(() => ({push: vi.fn()}))

vi.mock("vue-router", () => ({
    useRouter: () => ({push}),
    // Mirrors vue-router's guardEvent, so the tests see the production flow: the clicks RouterLink
    // handles are preventDefault-ed, the ones the browser turns into a new tab are left alone.
    RouterLink: {
        props: ["to"],
        template: "<a href=\"#\" @click=\"guardEvent\"><slot /></a>",
        methods: {
            guardEvent(event: MouseEvent) {
                if (event.metaKey || event.altKey || event.ctrlKey || event.shiftKey || event.button !== 0) {
                    return
                }
                event.preventDefault()
            },
        },
    },
}))

import NavBarAction from "./NavBarAction.vue"
import NavBarActionsDropdown from "./NavBarActionsDropdown.vue"
import {asItemKey} from "./navBarActionsContext"

const to = {name: "flows/update/edit"}

const mountItem = () =>
    mount(NavBarAction, {
        props: {to, label: "Edit flow"},
        global: {
            provide: {[asItemKey as symbol]: true},
            stubs: {KsDropdownItem: {template: "<li><slot /></li>"}},
        },
    })

const settle = () => new Promise((resolve) => setTimeout(resolve, 200))

const openMenu = async () => {
    const wrapper = mount(NavBarActionsDropdown, {
        attachTo: document.body,
        slots: {default: () => [h(NavBarAction, {to, label: "Edit flow"})]},
        global: {plugins: [KestraDesignSystem], mocks: {$t: (key: string) => key}},
    })

    await wrapper.find("button").trigger("click")
    await settle()

    return wrapper
}

const isMenuOpen = () => document.body.querySelector("[aria-haspopup=menu]")?.getAttribute("aria-expanded") === "true"

const link = () => document.body.querySelector("li a")

describe("NavBarAction", () => {
    beforeEach(() => push.mockReset())

    it("navigates when the row around the link is clicked", async () => {
        const wrapper = mountItem()

        await wrapper.find("li").trigger("click")

        expect(push).toHaveBeenCalledWith(to)
    })

    it("leaves the current tab where it is on a modifier click", async () => {
        const wrapper = mountItem()

        await wrapper.find("a").trigger("click", {metaKey: true})

        expect(push).not.toHaveBeenCalled()
    })

    it("does not navigate twice when the link itself was clicked", async () => {
        const wrapper = mountItem()

        await wrapper.find("a").trigger("click")

        expect(push).not.toHaveBeenCalled()
    })

    it("closes the menu as soon as the link is clicked", async () => {
        await openMenu()
        expect(isMenuOpen()).toBe(true)

        link()?.dispatchEvent(new MouseEvent("click", {bubbles: true, cancelable: true}))
        await settle()

        expect(isMenuOpen()).toBe(false)
    })

    it("closes the menu when the link is right-clicked", async () => {
        await openMenu()

        link()?.dispatchEvent(new MouseEvent("contextmenu", {bubbles: true, cancelable: true}))
        await settle()

        expect(isMenuOpen()).toBe(false)
    })

    it("closes the menu when the link is middle-clicked", async () => {
        await openMenu()

        link()?.dispatchEvent(new MouseEvent("auxclick", {bubbles: true, cancelable: true, button: 1}))
        await settle()

        expect(isMenuOpen()).toBe(false)
    })
})
