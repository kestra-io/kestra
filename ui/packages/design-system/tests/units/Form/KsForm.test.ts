import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsForm from "../../../src/components/Form/KsForm/KsForm.vue"
import KsFormItem from "../../../src/components/Form/KsForm/KsFormItem.vue"

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
        expect(typeof (wrapper.vm as any).validate).toBe("function")
    })

    test("exposes resetFields method", () => {
        const wrapper = mount(KsForm, {
            props: {model: {}},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).resetFields).toBe("function")
    })

    test("exposes clearValidate method", () => {
        const wrapper = mount(KsForm, {
            props: {model: {}},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).clearValidate).toBe("function")
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
})
