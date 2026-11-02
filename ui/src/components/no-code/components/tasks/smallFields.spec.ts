import {describe, test, expect, beforeEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {KsSwitch} from "@kestra-io/design-system"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskBoolean from "./TaskBoolean.vue"
import TaskConstant from "./TaskConstant.vue"
import TaskVersion from "./TaskVersion.vue"
import TaskLabelWithBoolean from "./TaskLabelWithBoolean.vue"
import InputText from "../inputs/InputText.vue"

describe("smallFields", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    describe("TaskBoolean", () => {
        test("renders the current value and emits update:modelValue when toggled", async () => {
            const wrapper = i18nMount(TaskBoolean, {
                props: {modelValue: false},
            })

            const switchComponent = wrapper.findComponent(KsSwitch)
            expect(switchComponent.exists()).toBe(true)
            expect(switchComponent.props("modelValue")).toBe(false)

            await switchComponent.vm.$emit("update:modelValue", true)
            expect(wrapper.emitted("update:modelValue")).toEqual([[true]])
        })

        test("falls back to the schema default when no value is given", () => {
            const wrapper = i18nMount(TaskBoolean, {
                props: {
                    schema: {default: true},
                },
            })

            const switchComponent = wrapper.findComponent(KsSwitch)
            expect(switchComponent.props("modelValue")).toBe(true)
        })
    })

    describe("TaskConstant", () => {
        test("renders its constant and does not emit on interaction", async () => {
            const wrapper = i18nMount(TaskConstant, {
                props: {
                    modelValue: "io.kestra.plugin.core.log.Log",
                    schema: {const: "io.kestra.plugin.core.log.Log"},
                },
            })

            expect(wrapper.text()).toBe("io.kestra.plugin.core.log.Log")

            await wrapper.trigger("click")
            expect(wrapper.emitted("update:modelValue")).toBeUndefined()
        })
    })

    describe("TaskVersion", () => {
        test("renders the version value and emits a change", async () => {
            const wrapper = i18nMount(TaskVersion, {
                props: {
                    modelValue: "1.2.3",
                },
            })

            const inputComponent = wrapper.findComponent(InputText)
            expect(inputComponent.exists()).toBe(true)
            expect(inputComponent.props("modelValue")).toBe("1.2.3")

            await inputComponent.vm.$emit("update:modelValue", "1.2.4")
            expect(wrapper.emitted("update:modelValue")).toEqual([["1.2.4"]])
        })
    })

    describe("TaskLabelWithBoolean", () => {
        test("renders its label alongside the control", () => {
            const wrapper = i18nMount(TaskLabelWithBoolean, {
                props: {
                    isBoolean: true,
                    componentProps: {modelValue: true},
                },
            })

            const booleanComponent = wrapper.findComponent(TaskBoolean)
            expect(booleanComponent.exists()).toBe(true)
            expect(booleanComponent.props("modelValue")).toBe(true)

            const notBooleanWrapper = i18nMount(TaskLabelWithBoolean, {
                props: {
                    isBoolean: false,
                },
            })
            expect(notBooleanWrapper.findComponent(TaskBoolean).exists()).toBe(false)
        })
    })

    test("each of the four renders without throwing when given no props at all", () => {
        expect(() => i18nMount(TaskBoolean)).not.toThrow()
        expect(() => i18nMount(TaskConstant)).not.toThrow()
        expect(() => i18nMount(TaskVersion)).not.toThrow()
        expect(() => i18nMount(TaskLabelWithBoolean)).not.toThrow()
    })
})
