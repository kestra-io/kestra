import {describe, test, expect} from "vitest"
import {markRaw} from "vue"
import {mount} from "@vue/test-utils"
import {RouterLink} from "vue-router"
import KestraDesignSystem from "../../../index"
import KsButton from "./KsButton.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsButton", () => {
    test("renders a button element", () => {
        const wrapper = mount(KsButton, {
            slots: {default: "Click me"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").exists()).toBe(true)
        expect(wrapper.text()).toBe("Click me")
    })

    test("type prop applies the correct class", () => {
        const wrapper = mount(KsButton, {
            props: {type: "primary"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button--primary").exists()).toBe(true)
    })

    test("small size applies kel-button--small class", () => {
        const wrapper = mount(KsButton, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button--small").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsButton, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-disabled").exists()).toBe(true)
    })

    test("loading applies is-loading class", () => {
        const wrapper = mount(KsButton, {
            props: {loading: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-loading").exists()).toBe(true)
    })

    test("plain applies is-plain class", () => {
        const wrapper = mount(KsButton, {
            props: {plain: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-plain").exists()).toBe(true)
    })

    test("round applies is-round class", () => {
        const wrapper = mount(KsButton, {
            props: {round: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-round").exists()).toBe(true)
    })

    test("circle applies is-circle class", () => {
        const wrapper = mount(KsButton, {
            props: {circle: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-circle").exists()).toBe(true)
    })

    test("square applies is-square class", () => {
        const wrapper = mount(KsButton, {
            props: {square: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-square").exists()).toBe(true)
    })

    test("square is not forwarded as a DOM attribute", () => {
        const wrapper = mount(KsButton, {
            props: {square: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").attributes("square")).toBeUndefined()
    })

    test("no is-square class without the square prop", () => {
        const wrapper = mount(KsButton, {
            global: globalConfig,
        })
        expect(wrapper.find(".is-square").exists()).toBe(false)
    })

    test("emits click event when clicked", async () => {
        const wrapper = mount(KsButton, {
            slots: {default: "Click"},
            global: globalConfig,
        })
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.emitted("click")).toBeTruthy()
    })

    test("does not emit click when disabled", async () => {
        const wrapper = mount(KsButton, {
            props: {disabled: true},
            slots: {default: "Click"},
            global: globalConfig,
        })
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.emitted("click")).toBeFalsy()
    })

    test("link prop applies is-link class", () => {
        const wrapper = mount(KsButton, {
            props: {link: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-link").exists()).toBe(true)
    })

    test("text prop applies is-text class", () => {
        const wrapper = mount(KsButton, {
            props: {text: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button.is-text").exists()).toBe(true)
    })

    test("tooltip prop renders the button and derives aria-label from it", () => {
        const wrapper = mount(KsButton, {
            props: {tooltip: "Delete"},
            slots: {default: "x"},
            global: globalConfig,
        })
        const button = wrapper.find(".kel-button")
        expect(button.exists()).toBe(true)
        expect(button.attributes("aria-label")).toBe("Delete")
    })

    test("without tooltip no aria-label is derived", () => {
        const wrapper = mount(KsButton, {
            slots: {default: "x"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").attributes("aria-label")).toBeUndefined()
    })

    test("explicit aria-label overrides the tooltip-derived one", () => {
        const wrapper = mount(KsButton, {
            props: {tooltip: "Delete"},
            attrs: {"aria-label": "Custom label"},
            slots: {default: "x"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").attributes("aria-label")).toBe("Custom label")
    })

    test("adds the block class when block is set", () => {
        const wrapper = mount(KsButton, {
            props: {block: true},
            slots: {default: "x"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").classes()).toContain("is-block")
        expect(wrapper.find(".kel-button").attributes("block")).toBeUndefined()
    })

    test("passes href, target and rel to the anchor it renders with tag a", () => {
        const wrapper = mount(KsButton, {
            props: {tag: "a", href: "https://kestra.io", target: "_blank", rel: "noopener"},
            global: globalConfig,
        })
        const anchor = wrapper.find("a.kel-button")
        expect(anchor.attributes("href")).toBe("https://kestra.io")
        expect(anchor.attributes("target")).toBe("_blank")
        expect(anchor.attributes("rel")).toBe("noopener")
    })

    test("passes to to the RouterLink it renders", () => {
        const wrapper = mount(KsButton, {
            props: {tag: markRaw(RouterLink), to: {name: "docs"}},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "RouterLink"}).props("to")).toStrictEqual({name: "docs"})
    })

    test("renders no RouterLink attributes on a plain button", () => {
        const wrapper = mount(KsButton, {global: globalConfig})
        expect(wrapper.find("button.kel-button").attributes("replace")).toBeUndefined()
        expect(wrapper.find("button.kel-button").attributes("viewtransition")).toBeUndefined()
    })
})
