import {describe, test, expect} from "vitest"
import KsEmpty from "../../../src/components/Data/KsEmpty.vue"
import {i18nMount} from "../i18nMount"

const globalConfig = {}

describe("KsEmpty", () => {
    test("renders empty element", () => {
        const wrapper = i18nMount(KsEmpty, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-empty").exists()).toBe(true)
    })

    test("description prop renders text", () => {
        const wrapper = i18nMount(KsEmpty, {
            props: {description: "No data found"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("No data found")
    })

    test("default slot renders action content", () => {
        const wrapper = i18nMount(KsEmpty, {
            slots: {default: "<button>Create</button>"},
            global: globalConfig,
        })
        expect(wrapper.find("button").exists()).toBe(true)
    })

    test("keeps the surface background by default", () => {
        const wrapper = i18nMount(KsEmpty, {global: globalConfig})
        expect(wrapper.find(".kel-empty").classes()).not.toContain("kel-empty--no-background")
    })

    test("drops the background when background is false", () => {
        const wrapper = i18nMount(KsEmpty, {
            props: {background: false},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-empty").classes()).toContain("kel-empty--no-background")
    })
})
