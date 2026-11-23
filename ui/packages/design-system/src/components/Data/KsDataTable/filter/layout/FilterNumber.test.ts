import {describe, test, expect} from "vitest"
import FilterNumber from "./FilterNumber.vue"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

// Feeds each emitted value back as the prop, as a parent's v-model does; otherwise the input resets to the old value.
const mountWithModel = (props: {modelValue: string | string[]; range?: boolean; label?: string}) => {
    const wrapper = i18nMount(FilterNumber, {
        props: {
            ...props,
            "onUpdate:modelValue": (value: string | string[]) => wrapper.setProps({modelValue: value}),
        },
    })
    return wrapper
}

const typeInto = async (wrapper: ReturnType<typeof i18nMount>, selector: string, text: string) => {
    const input = wrapper.find(`${selector} input`)
    await input.setValue(text)
    await input.trigger("change")
}

describe("FilterNumber", () => {
    test("shouldKeepDecimalsWhenTypingASingleValue", async () => {
        const wrapper = mountWithModel({modelValue: "", label: "Duration"})

        await typeInto(wrapper, "[data-test='filter-number-value']", "1.25")

        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["1.25"])
    })

    test("shouldKeepDecimalsOnBothBoundsWhenTypingARange", async () => {
        const wrapper = mountWithModel({modelValue: ["0.5", ""], range: true})

        await typeInto(wrapper, "[data-test='filter-number-max']", "2.75")

        expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([["0.5", "2.75"]])
    })

    test("shouldShowDecimalBoundsWhenOpenedOnAnAppliedRange", () => {
        const wrapper = i18nMount(FilterNumber, {props: {modelValue: ["0.5", "2.75"], range: true}})

        const [min, max] = wrapper.findAll("input").map(input => (input.element as HTMLInputElement).value)
        expect([min, max]).toEqual(["0.5", "2.75"])
    })
})
