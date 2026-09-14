import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"

const {push} = vi.hoisted(() => ({push: vi.fn()}))

vi.mock("vue-router", () => ({
    useRouter: () => ({push}),
    RouterLink: {
        props: ["to"],
        template: "<a href=\"#\" @click=\"$event.preventDefault()\"><slot /></a>",
    },
}))

import NavBarAction from "./NavBarAction.vue"
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

describe("NavBarAction", () => {
    beforeEach(() => push.mockReset())

    it("navigates when the row around the link is clicked", async () => {
        const wrapper = mountItem()

        await wrapper.find("li").trigger("click")

        expect(push).toHaveBeenCalledWith(to)
    })

    it("leaves the current tab where it is on a modifier click", async () => {
        const wrapper = mountItem()

        await wrapper.find("li").trigger("click", {metaKey: true})

        expect(push).not.toHaveBeenCalled()
    })

    it("does not navigate twice when the link itself was clicked", async () => {
        const wrapper = mountItem()

        await wrapper.find("a").trigger("click")

        expect(push).not.toHaveBeenCalled()
    })
})
