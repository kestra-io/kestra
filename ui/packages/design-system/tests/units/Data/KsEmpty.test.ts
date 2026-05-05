import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsEmpty from "../../../src/components/Data/KsEmpty.vue"

const i18n = createI18n({legacy: false, locale: "en", messages: {}})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

describe("KsEmpty", () => {
    test("renders empty element", () => {
        const wrapper = mount(KsEmpty, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-empty").exists()).toBe(true)
    })

    test("description prop renders text", () => {
        const wrapper = mount(KsEmpty, {
            props: {description: "No data found"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("No data found")
    })

    test("default slot renders action content", () => {
        const wrapper = mount(KsEmpty, {
            slots: {default: "<button>Create</button>"},
            global: globalConfig,
        })
        expect(wrapper.find("button").exists()).toBe(true)
    })
})
