import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsPassword from "../../../src/components/Form/KsPassword.vue"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}
const mountPassword = (props: Record<string, unknown>) => mount(KsPassword, {props, global: globalConfig})

describe("KsPassword", () => {
    test("masks by default, and the toggle switches both ways", async () => {
        const wrapper = mountPassword({modelValue: "secret"})
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)

        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.find(".ks-password--masked").exists()).toBe(false)

        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)
    })

    test("offers the toggle only for an enabled field holding a value", () => {
        expect(mountPassword({modelValue: ""}).find(".kel-button").exists()).toBe(false)
        expect(mountPassword({modelValue: "secret", disabled: true}).find(".kel-button").exists()).toBe(false)
        expect(mountPassword({modelValue: "secret"}).find(".kel-button").exists()).toBe(true)
    })

    test("re-masks a revealed value when the field becomes disabled", async () => {
        const wrapper = mountPassword({modelValue: "secret", disabled: false})
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.find(".ks-password--masked").exists()).toBe(false)

        await wrapper.setProps({disabled: true})
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)
    })
})
