import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsPasswordRequirements from "../../../src/components/Form/KsPasswordRequirements.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

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
})
