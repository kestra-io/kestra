import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskDuration from "./TaskDuration.vue"

const KsInputNumberStub = {
    name: "KsInputNumber",
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<input :value='modelValue' />",
}

const KsSelectStub = {
    name: "KsSelect",
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<div><slot /></div>",
}

const KsOptionStub = {
    name: "KsOption",
    props: ["label", "value"],
    template: "<div />",
}

const KsIconButtonStub = {
    name: "KsIconButton",
    template: "<button><slot /></button>",
}

function mountDuration(modelValue: string) {
    return i18nMount(TaskDuration, {
        props: {modelValue},
        global: {
            stubs: {
                KsInputNumber: KsInputNumberStub,
                KsSelect: KsSelectStub,
                KsOption: KsOptionStub,
                KsIconButton: KsIconButtonStub,
            },
        },
    })
}

describe("TaskDuration", () => {
    test("emits a single week as days", async () => {
        const wrapper = mountDuration("P2W")
        const inputs = wrapper.findAllComponents({name: "KsInputNumber"})

        expect(inputs).toHaveLength(1)

        await inputs[0].vm.$emit("update:modelValue", 2)

        expect(wrapper.emitted("update:modelValue")).toEqual([["P14D"]])
    })

    test("emits weeks plus days and hours as days and hours", async () => {
        const wrapper = mountDuration("P1W3DT4H")
        const inputs = wrapper.findAllComponents({name: "KsInputNumber"})

        expect(inputs).toHaveLength(3)

        await inputs[2].vm.$emit("update:modelValue", 4)

        expect(wrapper.emitted("update:modelValue")).toEqual([["P10DT4H"]])
    })

    test("leaves day and time-only durations unchanged", async () => {
        const wrapper = mountDuration("P1DT2H")
        const inputs = wrapper.findAllComponents({name: "KsInputNumber"})

        expect(inputs).toHaveLength(2)

        await inputs[0].vm.$emit("update:modelValue", 1)

        expect(wrapper.emitted("update:modelValue")).toEqual([["P1DT2H"]])
    })
})
