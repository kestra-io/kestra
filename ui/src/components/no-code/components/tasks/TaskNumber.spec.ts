import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskNumber from "./TaskNumber.vue"

function mountNumber(modelValue?: unknown, schema: Record<string, unknown> = {type: "number"}) {
    return mount(TaskNumber, {
        props: {modelValue: modelValue as never, schema},
        global: {plugins: [KestraDesignSystem]},
    })
}

function shown(wrapper: ReturnType<typeof mountNumber>) {
    return (wrapper.find("input").element as HTMLInputElement).value
}

describe("TaskNumber", () => {
    it("shows decimal values unchanged", () => {
        expect(shown(mountNumber(2.5))).toBe("2.5")
        expect(shown(mountNumber(0.75))).toBe("0.75")
    })

    it("shows 0 instead of an empty field", () => {
        expect(shown(mountNumber(0))).toBe("0")
    })

    it("shows integers and numeric strings", () => {
        expect(shown(mountNumber(3))).toBe("3")
        expect(shown(mountNumber("4.5"))).toBe("4.5")
    })

    it("leaves the field empty for no value or a non-numeric expression", () => {
        expect(shown(mountNumber(undefined))).toBe("")
        expect(shown(mountNumber("{{ inputs.ratio }}"))).toBe("")
    })

    it("falls back to the schema default", () => {
        expect(shown(mountNumber(undefined, {type: "number", default: 1.5}))).toBe("1.5")
    })

    it("keeps the decimal when the user types one", async () => {
        const wrapper = mountNumber(undefined)
        await wrapper.find("input").setValue("2.5")
        const emitted = wrapper.emitted("update:modelValue") ?? []
        expect(emitted.at(-1)).toEqual([2.5])
        await wrapper.setProps({modelValue: 2.5})
        expect(shown(wrapper)).toBe("2.5")
    })
})
