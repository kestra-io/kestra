import {describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {ElOptionGroup} from "element-plus"
import * as filteredProps from "../../utils/filteredProps"
import KsOptionGroup from "./KsOptionGroup.vue"

describe("KsOptionGroup", () => {
    test("forwards label and disabled to the option group", () => {
        const wrapper = mount(KsOptionGroup, {
            props: {label: "Available options", disabled: true},
        })

        expect(wrapper.findComponent(ElOptionGroup).props()).toMatchObject({
            label: "Available options",
            disabled: true,
        })
    })

    test("renders the default slot when supplied", () => {
        const wrapper = mount(KsOptionGroup, {
            slots: {default: '<span data-test="option-content">Option content</span>'},
        })

        expect(wrapper.find('[data-test="option-content"]').text()).toBe("Option content")
    })

    test("does not forward a default slot when absent", () => {
        const wrapper = mount(KsOptionGroup)

        expect(wrapper.findComponent(ElOptionGroup).vm.$slots.default).toBeUndefined()
        expect(wrapper.text()).toBe("")
    })

    test("forwards attrs explicitly without automatic fallthrough", () => {
        const wrapper = mount(KsOptionGroup, {
            attrs: {class: "caller-group", "data-test": "option-group"},
        })

        const group = wrapper.findComponent(ElOptionGroup)
        expect(wrapper.vm.$options.inheritAttrs).toBe(false)
        expect(group.vm.$attrs).toMatchObject({class: "caller-group", "data-test": "option-group"})
        expect(group.vm.$.vnode.props?.class).toBe("caller-group")
    })

    test("inherited attrs override colliding filtered props", () => {
        // Vue consumes declared props before building $attrs, so a synthetic key is needed to exercise a collision.
        const spy = vi.spyOn(filteredProps, "useFilteredProps")

        try {
            spy.mockImplementationOnce(() => () => ({"data-test": "from-filtered-props"}))
            const wrapper = mount(KsOptionGroup, {
                attrs: {"data-test": "from-attrs"},
            })

            const forwardedProps = wrapper.findComponent(ElOptionGroup).vm.$.vnode.props
            expect(forwardedProps?.["data-test"]).toBe("from-attrs")
        } finally {
            spy.mockRestore()
        }
    })

    test("omits an undefined label while preserving Boolean casting for disabled", () => {
        const wrapper = mount(KsOptionGroup)

        const forwardedProps = wrapper.findComponent(ElOptionGroup).vm.$.vnode.props
        expect(forwardedProps).not.toHaveProperty("label")
        expect(forwardedProps).toHaveProperty("disabled", false)
    })
})
