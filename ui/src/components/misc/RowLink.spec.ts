import {describe, expect, test} from "vitest"
import {mount, shallowMount} from "@vue/test-utils"
import RowLink from "./RowLink.vue"

const mountRowLink = (props: {text: string; icon?: string; clickable?: boolean}) =>
    shallowMount(RowLink, {props})

describe("RowLink", () => {
    test("renders the text", () => {
        const wrapper = mountRowLink({text: "My plugin"})

        expect(wrapper.find(".text").text()).toBe("My plugin")
    })

    test("renders an icon only when provided", () => {
        const withoutIcon = mountRowLink({text: "Without icon"})
        expect(withoutIcon.find(".icon").exists()).toBe(false)

        const withIcon = mountRowLink({text: "With icon", icon: "plugin-icon"})
        expect(withIcon.find(".icon").exists()).toBe(true)
        expect(withIcon.find(".icon").attributes("cls")).toBe("plugin-icon")
    })

    test("defaults to clickable and applies its class", () => {
        const wrapper = mountRowLink({text: "Click me"})

        expect(wrapper.find(".row-link").classes()).toContain("clickable")
    })

    test("removes the clickable class when disabled", () => {
        const wrapper = mountRowLink({text: "Not clickable", clickable: false})

        expect(wrapper.find(".row-link").classes()).not.toContain("clickable")
    })

    test.each([true, false])("emits click exactly once when clickable is %s", async (clickable) => {
        const wrapper = mountRowLink({text: "A row", clickable})

        await wrapper.find(".row-link").trigger("click")

        expect(wrapper.emitted("click")).toHaveLength(1)
    })

    test.each([true, false])("renders the chevron when clickable is %s", (clickable) => {
        // Fully mounted on purpose: a shallow stub is named after the local import, so it
        // would stay green even if the chevron icon itself were swapped for another one.
        const wrapper = mount(RowLink, {props: {text: "A row", clickable}})

        expect(wrapper.find(".chevron-right-icon").exists()).toBe(true)
    })
})
