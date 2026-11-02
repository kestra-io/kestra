import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, nextTick} from "vue"
import KsCarouselItem from "./KsCarouselItem.vue"

const ElCarouselItemStub = defineComponent({
    name: "ElCarouselItem",
    inheritAttrs: false,
    setup(_, {attrs, slots}) {
        return () => h("div", {...attrs, "data-has-default-slot": String(Boolean(slots.default))}, slots.default?.())
    },
})

const globalConfig = {stubs: {ElCarouselItem: ElCarouselItemStub}}

describe("KsCarouselItem", () => {
    test("forwards name and label to the carousel item", () => {
        const wrapper = mount(KsCarouselItem, {
            props: {name: "overview", label: 1},
            global: globalConfig,
        })

        expect(wrapper.findComponent(ElCarouselItemStub).vm.$attrs).toMatchObject({name: "overview", label: 1})
    })

    test("renders the default slot only when provided", () => {
        const withSlot = mount(KsCarouselItem, {
            slots: {default: "Carousel content"},
            global: globalConfig,
        })
        const withoutSlot = mount(KsCarouselItem, {global: globalConfig})

        expect(withSlot.text()).toBe("Carousel content")
        expect(withSlot.attributes("data-has-default-slot")).toBe("true")
        expect(withoutSlot.attributes("data-has-default-slot")).toBe("false")
    })

    test("forwards inherited attributes to the carousel item", () => {
        const wrapper = mount(KsCarouselItem, {
            attrs: {class: "custom-carousel-item", "data-testid": "carousel-item"},
            global: globalConfig,
        })

        expect(wrapper.attributes("class")).toBe("custom-carousel-item")
        expect(wrapper.attributes("data-testid")).toBe("carousel-item")
    })

    test("lets an inherited attribute override a filtered prop with the same name", async () => {
        const wrapper = mount(KsCarouselItem, {
            props: {name: "from-prop"},
            global: globalConfig,
        })
        // Vue normally consumes attributes matching declared props before they reach $attrs.
        wrapper.vm.$.attrs.name = "from-attribute"
        wrapper.vm.$forceUpdate()
        await nextTick()

        expect(wrapper.findComponent(ElCarouselItemStub).vm.$attrs.name).toBe("from-attribute")
    })

    test("does not forward undefined props", () => {
        const wrapper = mount(KsCarouselItem, {
            props: {name: undefined, label: undefined},
            global: globalConfig,
        })

        expect(wrapper.findComponent(ElCarouselItemStub).vm.$attrs).not.toHaveProperty("name")
        expect(wrapper.findComponent(ElCarouselItemStub).vm.$attrs).not.toHaveProperty("label")
    })
})
