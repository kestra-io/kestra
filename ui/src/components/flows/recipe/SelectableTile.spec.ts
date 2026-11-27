import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import SelectableTile from "./SelectableTile.vue"

describe("SelectableTile", () => {
    test("emits select when clicked", async () => {
        const wrapper = mount(SelectableTile)

        await wrapper.find("button").trigger("click")

        expect(wrapper.emitted("select")).toHaveLength(1)
    })

    test("a disabled tile does not emit select when clicked", async () => {
        const wrapper = mount(SelectableTile, {props: {disabled: true}})
        const button = wrapper.find("button")

        await button.trigger("click")

        expect(wrapper.emitted("select")).toBeUndefined()
        expect(button.attributes("disabled")).toBeDefined()
        expect(button.attributes("aria-disabled")).toBe("true")
    })

    test("renders the config slot when the tile is selected and has a config slot", () => {
        const wrapper = mount(SelectableTile, {
            props: {selected: true},
            slots: {config: "<span class='my-config'>Config</span>"},
        })

        expect(wrapper.find(".tile-config").exists()).toBe(true)
        expect(wrapper.find(".my-config").exists()).toBe(true)
        expect(wrapper.classes()).toContain("has-config")
    })

    test("does not render the config slot when the tile is not selected", () => {
        const wrapper = mount(SelectableTile, {
            props: {selected: false},
            slots: {config: "<span class='my-config'>Config</span>"},
        })

        expect(wrapper.find(".tile-config").exists()).toBe(false)
        expect(wrapper.find(".my-config").exists()).toBe(false)
        expect(wrapper.classes()).not.toContain("has-config")
    })

    test("a selected tile with no config slot renders no config", () => {
        const wrapper = mount(SelectableTile, {props: {selected: true}})

        expect(wrapper.find(".tile-config").exists()).toBe(false)
        expect(wrapper.classes()).not.toContain("has-config")
    })
})