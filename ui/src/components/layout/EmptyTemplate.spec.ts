import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import EmptyTemplate from "./EmptyTemplate.vue"

describe("EmptyTemplate", () => {
    test("mounts with no props without throwing", () => {
        expect(() => mount(EmptyTemplate)).not.toThrow()
    })

    test("renders the default slot", () => {
        const wrapper = mount(EmptyTemplate, {
            slots: {default: "<p>No data available</p>"},
        })

        expect(wrapper.text()).toContain("No data available")
    })

    test("defaults plain to false and omits the plain class", () => {
        const wrapper = mount(EmptyTemplate)

        expect(wrapper.props("plain")).toBe(false)
        expect(wrapper.classes()).not.toContain("plain")
    })

    test("adds the plain class when plain is true", () => {
        const wrapper = mount(EmptyTemplate, {
            props: {plain: true},
        })

        expect(wrapper.classes()).toContain("plain")
    })

    test("retains the container classes in both variants", () => {
        const defaultWrapper = mount(EmptyTemplate)
        expect(defaultWrapper.classes()).toContain("container")
        expect(defaultWrapper.classes()).toContain("empty-template")

        const plainWrapper = mount(EmptyTemplate, {
            props: {plain: true},
        })
        expect(plainWrapper.classes()).toContain("container")
        expect(plainWrapper.classes()).toContain("empty-template")
    })
})

