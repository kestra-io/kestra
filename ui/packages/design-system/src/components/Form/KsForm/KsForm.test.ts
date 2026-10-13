import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../index"
import KsForm from "./KsForm.vue"
import KsFormItem from "./KsFormItem.vue"

type KsFormExposed = InstanceType<typeof KsForm>

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsForm", () => {
    test("renders form element", () => {
        const wrapper = mount(KsForm, {
            props: {model: {}},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-form").exists()).toBe(true)
    })

    test("renders form items", () => {
        const wrapper = mount(KsForm, {
            props: {model: {}},
            slots: {
                default: "<ks-form-item label=\"Name\"><input /></ks-form-item>",
            },
            global: globalConfig,
        })
        expect(wrapper.find(".kel-form").exists()).toBe(true)
    })

    test("exposes validate method", () => {
        const wrapper = mount(KsForm, {
            props: {model: {}},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as KsFormExposed).validate).toBe("function")
    })

    test("exposes resetFields method", () => {
        const wrapper = mount(KsForm, {
            props: {model: {}},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as KsFormExposed).resetFields).toBe("function")
    })

    test("exposes clearValidate method", () => {
        const wrapper = mount(KsForm, {
            props: {model: {}},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as KsFormExposed).clearValidate).toBe("function")
    })
})

describe("KsFormItem", () => {
    test("adds is-inline-row class when inline", () => {
        const wrapper = mount(KsFormItem, {
            props: {label: "Name", inline: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-form-item.is-inline-row").exists()).toBe(true)
    })

    test("omits is-inline-row class by default", () => {
        const wrapper = mount(KsFormItem, {
            props: {label: "Name"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-form-item.is-inline-row").exists()).toBe(false)
    })

    test("forwards for so the label can opt out of pointing at a control", () => {
        const wrapper = mount(KsFormItem, {
            props: {label: "Name", for: ""},
            slots: {default: "<ks-input />"},
            global: globalConfig,
        })
        const label = wrapper.find(".kel-form-item__label")
        expect(label.element.tagName).toBe("DIV")
        expect(label.attributes("for")).toBe("")
    })

    test("leaves for unset so element-plus can derive it", () => {
        const wrapper = mount(KsFormItem, {
            props: {label: "Name"},
            slots: {default: "<ks-input />"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-form-item__label").attributes("for")).toBeUndefined()
    })
})
