import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsPasswordRequirements from "../../../src/components/Form/KsPasswordRequirements.vue"

const i18n = createI18n({legacy: false, locale: "en", messages: {}})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

describe("KsPasswordRequirements", () => {
    test("renders the four policy rules", () => {
        const wrapper = mount(KsPasswordRequirements, {props: {password: ""}, global: globalConfig})
        expect(wrapper.findAll(".ks-password-requirements__item").length).toBe(4)
    })

    test("marks every rule as met for a valid password", () => {
        const wrapper = mount(KsPasswordRequirements, {props: {password: "Abcdefg1"}, global: globalConfig})
        expect(wrapper.findAll(".ks-check-item.is-met").length).toBe(4)
    })

    test("does not mark all rules for an incomplete password", () => {
        const wrapper = mount(KsPasswordRequirements, {props: {password: "abc"}, global: globalConfig})
        expect(wrapper.findAll(".ks-check-item.is-met").length).toBeLessThan(4)
    })

    test("emits update:valid reflecting the password", () => {
        const valid = mount(KsPasswordRequirements, {props: {password: "Abcdefg1"}, global: globalConfig})
        expect(valid.emitted("update:valid")?.at(-1)).toEqual([true])

        const invalid = mount(KsPasswordRequirements, {props: {password: "abc"}, global: globalConfig})
        expect(invalid.emitted("update:valid")?.at(-1)).toEqual([false])
    })

    test("renders a custom rules list with explicit labels", () => {
        const rules = [
            {key: "length", label: "At least 12 characters", test: (p: string) => p.length >= 12},
            {key: "special", label: "One special character", test: (p: string) => /[!@#$%^&*]/.test(p)},
        ]
        const wrapper = mount(KsPasswordRequirements, {props: {password: "abcdefghijkl!", rules}, global: globalConfig})
        const items = wrapper.findAll(".ks-password-requirements__item")
        expect(items.length).toBe(2)
        expect(wrapper.text()).toContain("At least 12 characters")
        expect(wrapper.text()).toContain("One special character")
        expect(wrapper.findAll(".ks-check-item.is-met").length).toBe(2)
    })

    test("custom rules drive update:valid", () => {
        const rules = [{key: "length", label: "At least 12 characters", test: (p: string) => p.length >= 12}]
        const tooShort = mount(KsPasswordRequirements, {props: {password: "abc", rules}, global: globalConfig})
        expect(tooShort.emitted("update:valid")?.at(-1)).toEqual([false])
    })
})
