import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import TaskNumber from "./TaskNumber.vue"

const stubs = {
    KsInputNumber: {
        name: "KsInputNumber",
        props: ["modelValue", "state", "min", "max", "step", "type"],
        template: "<input class='ks-input-number' :value='modelValue' />",
    },
}

describe("TaskNumber", () => {
    it("renders decimal numbers without truncating to integer", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: 2.5,
                schema: {type: "number"},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("modelValue")).toBe(2.5)
    })

    it("renders 0 instead of treating it as an empty field", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: 0,
                schema: {type: "number"},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("modelValue")).toBe(0)
    })

    it("renders 0 from schema default when modelValue is omitted", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: undefined,
                schema: {type: "number", default: 0},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("modelValue")).toBe(0)
    })

    it("uses schema default when modelValue is undefined", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: undefined,
                schema: {type: "number", default: 42},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("modelValue")).toBe(42)
    })

    it("prioritizes modelValue over schema default", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: 7.2,
                schema: {type: "number", default: 42},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("modelValue")).toBe(7.2)
    })

    it("passes minimum, maximum, and step constraints to KsInputNumber", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: 1.5,
                schema: {
                    type: "number",
                    minimum: 0.5,
                    maximum: 10.5,
                    step: 0.1,
                },
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("min")).toBe(0.5)
        expect(input.props("max")).toBe(10.5)
        expect(input.props("step")).toBe(0.1)
    })

    it("marks required field without value as invalid", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                required: true,
                modelValue: undefined,
                schema: {type: "number"},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("state")).toBe(false)
    })

    it("marks optional field without value as valid", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                required: false,
                modelValue: undefined,
                schema: {type: "number"},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("state")).toBe(true)
    })

    it("marks non-numeric modelValue as invalid", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: "not-a-number",
                schema: {type: "number"},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("state")).toBe(false)
    })

    it("leaves non-numeric Pebble expressions out of the numeric input", () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: "{{ inputs.my_ratio }}",
                schema: {type: "number"},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        expect(input.props("modelValue")).toBeUndefined()
    })

    it("emits update:modelValue when input changes", async () => {
        const wrapper = mount(TaskNumber, {
            props: {
                modelValue: 1,
                schema: {type: "number"},
            },
            global: {stubs},
        })

        const input = wrapper.findComponent({name: "KsInputNumber"})
        await input.vm.$emit("update:modelValue", 3.14)

        expect(wrapper.emitted("update:modelValue")).toBeTruthy()
        expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([3.14])
    })
})
