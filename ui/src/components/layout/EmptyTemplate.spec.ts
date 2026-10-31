import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import EmptyTemplate from "./EmptyTemplate.vue"

describe("EmptyTemplate", () => {
    test("renders the default slot and container classes", () => {
        const wrapper = mount(EmptyTemplate, {
            slots: {default: "No results"},
        })

        expect(wrapper.text()).toBe("No results")
        expect(wrapper.classes()).toEqual(["container", "empty-template"])
    })

    test("adds the plain class when requested", () => {
        const wrapper = mount(EmptyTemplate, {props: {plain: true}})

        expect(wrapper.classes()).toContain("plain")
    })

    test("does not add the plain class by default", () => {
        const wrapper = mount(EmptyTemplate)

        expect(wrapper.classes()).not.toContain("plain")
    })
})
