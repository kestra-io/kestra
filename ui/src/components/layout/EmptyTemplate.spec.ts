import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import EmptyTemplate from "./EmptyTemplate.vue"

describe("EmptyTemplate", () => {
    test("renders the default slot", () => {
        const wrapper = mount(EmptyTemplate, {slots: {default: "<p data-test=\"content\">Nothing here yet</p>"}})

        expect(wrapper.get("[data-test=content]").text()).toBe("Nothing here yet")
    })

    test("adds the plain class when plain is set, which drops the illustration", () => {
        const wrapper = mount(EmptyTemplate, {props: {plain: true}})

        expect(wrapper.classes()).toContain("plain")
    })

    test("leaves out the plain class by default", () => {
        const wrapper = mount(EmptyTemplate)

        expect(wrapper.props("plain")).toBe(false)
        expect(wrapper.classes()).not.toContain("plain")
    })

    test.each([false, true])("keeps the container classes when plain is %s", (plain) => {
        const wrapper = mount(EmptyTemplate, {props: {plain}})

        expect(wrapper.classes()).toEqual(expect.arrayContaining(["container", "empty-template"]))
    })

    test("mounts without any props", () => {
        expect(() => mount(EmptyTemplate)).not.toThrow()
    })
})
