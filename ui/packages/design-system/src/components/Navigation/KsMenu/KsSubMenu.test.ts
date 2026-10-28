import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, nextTick} from "vue"
import KsSubMenu from "./KsSubMenu.vue"

const ElSubMenuStub = defineComponent({
    name: "ElSubMenu",
    inheritAttrs: false,
    setup(_, {attrs, slots}) {
        return () => h(
            "div",
            {
                ...attrs,
                "data-has-default-slot": String(Boolean(slots.default)),
                "data-has-title-slot": String(Boolean(slots.title)),
            },
            [slots.title?.(), slots.default?.()],
        )
    },
})

const globalConfig = {stubs: {ElSubMenu: ElSubMenuStub}}

describe("KsSubMenu", () => {
    test("binds the required index to the sub menu", () => {
        const wrapper = mount(KsSubMenu, {
            props: {index: "flows"},
            global: globalConfig,
        })
        expect(wrapper.findComponent(ElSubMenuStub).vm.$attrs).toMatchObject({index: "flows"})
    })

    test("forwards the optional props to the sub menu", () => {
        const wrapper = mount(KsSubMenu, {
            props: {
                index: "flows",
                showTimeout: 50,
                hideTimeout: 100,
                popperClass: "my-popper",
                disabled: true,
                teleported: false,
                popperOffset: 12,
            },
            global: globalConfig,
        })
        expect(wrapper.findComponent(ElSubMenuStub).vm.$attrs).toMatchObject({
            showTimeout: 50,
            hideTimeout: 100,
            popperClass: "my-popper",
            disabled: true,
            teleported: false,
            popperOffset: 12,
        })
    })

    test("renders the default and title slots only when provided", () => {
        const withSlots = mount(KsSubMenu, {
            props: {index: "flows"},
            slots: {title: "Flows", default: "Sub menu content"},
            global: globalConfig,
        })
        const withoutSlots = mount(KsSubMenu, {
            props: {index: "flows"},
            global: globalConfig,
        })

        expect(withSlots.text()).toBe("FlowsSub menu content")
        expect(withSlots.attributes("data-has-default-slot")).toBe("true")
        expect(withSlots.attributes("data-has-title-slot")).toBe("true")
        expect(withoutSlots.text()).toBe("")
        expect(withoutSlots.attributes("data-has-default-slot")).toBe("false")
        expect(withoutSlots.attributes("data-has-title-slot")).toBe("false")
    })

    test("lets an inherited attribute override a filtered prop with the same name", async () => {
        const wrapper = mount(KsSubMenu, {
            props: {index: "flows", showTimeout: 50},
            global: globalConfig,
        })
        // Vue normally consumes attributes matching declared props before they reach $attrs.
        wrapper.vm.$.attrs.showTimeout = 999
        wrapper.vm.$forceUpdate()
        await nextTick()

        expect(wrapper.findComponent(ElSubMenuStub).vm.$attrs.showTimeout).toBe(999)
    })

    test("does not forward undefined props", () => {
        const wrapper = mount(KsSubMenu, {
            props: {
                index: "flows",
                showTimeout: undefined,
                hideTimeout: undefined,
                popperClass: undefined,
                disabled: undefined,
                teleported: undefined,
                popperOffset: undefined,
            },
            global: globalConfig,
        })

        const attrs = wrapper.findComponent(ElSubMenuStub).vm.$attrs
        expect(Object.keys(attrs)).toEqual(["index"])
    })
})
