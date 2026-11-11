import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KsSelectableListItem from "./KsSelectableListItem.vue"

describe("KsSelectableListItem", () => {
    test("renders title, subtitle and leading content", () => {
        const wrapper = mount(KsSelectableListItem, {
            props: {title: "Artifact", subtitle: "application/json"},
            slots: {leading: "Icon"},
        })

        expect(wrapper.element.tagName).toBe("BUTTON")
        expect(wrapper.text()).toContain("Artifact")
        expect(wrapper.text()).toContain("application/json")
        expect(wrapper.text()).toContain("Icon")
    })

    test("exposes the selected state accessibly", () => {
        const wrapper = mount(KsSelectableListItem, {props: {title: "Artifact", selected: true}})

        expect(wrapper.attributes("aria-current")).toBe("true")
        expect(wrapper.classes()).toContain("is-selected")
    })

    test("disables interaction when disabled", () => {
        const wrapper = mount(KsSelectableListItem, {props: {title: "Artifact", disabled: true}})

        expect(wrapper.attributes("disabled")).toBeDefined()
    })

    test("emits click when the item is clicked", async () => {
        const wrapper = mount(KsSelectableListItem, {
            props: {title: "Artifact"},
        })
        await wrapper.trigger("click")

        expect(wrapper.emitted("click")).toHaveLength(1)
    })

    test("uses the default slot instead of the title prop", () => {
        const wrapper = mount(KsSelectableListItem, {
            props: {title: "Prop title"},
            slots: {default: "Slot title"},
        })
        
        expect(wrapper.text()).toContain("Slot title")
        expect(wrapper.text()).not.toContain("Prop title")
    })

    test("supports inset styling", () => {
        const wrapper = mount(KsSelectableListItem, {
            props: {title: "Artifact", inset: true},
        })

        expect(wrapper.classes()).toContain("is-inset")
    })

    test("omits the subtitle when it is not provided", () => {
        const wrapper = mount(KsSelectableListItem, {
            props: {title: "Artifact"},
        })

        expect(wrapper.find(".ks-selectable-list-item__subtitle").exists()).toBe(false)
    })
})
