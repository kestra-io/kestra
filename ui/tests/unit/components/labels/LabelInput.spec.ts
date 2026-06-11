import {describe, expect, test} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import LabelInput from "../../../../src/components/labels/LabelInput.vue"

const KsButton = {
    name: "KsButton",
    props: ["icon", "tooltip", "type", "loading"],
    emits: ["click"],
    template: "<button @click=\"$emit('click')\"><slot/></button>",
}
const KsInput = {
    name: "KsInput",
    props: ["modelValue", "placeholder", "disabled"],
    emits: ["update:modelValue"],
    template: "<input :value=\"modelValue\" :disabled=\"disabled\" @input=\"$emit('update:modelValue', $event.target.value)\"/>",
}

const mountInput = async (props = {}) => {
    const wrapper = mount(LabelInput, {
        props: {labels: [], ...props},
        global: {mocks: {$t: (key: string) => key}, stubs: {KsButton, KsInput}},
    })
    await flushPromises()
    return wrapper
}

describe("LabelInput", () => {
    test("shows one empty row when there are no labels", async () => {
        const wrapper = await mountInput({labels: []})
        expect(wrapper.findAll(".label-input-row")).toHaveLength(1)
    })

    test("renders one row per provided label", async () => {
        const wrapper = await mountInput({labels: [{key: "a", value: "1"}, {key: "b", value: "2"}]})
        expect(wrapper.findAll(".label-input-row")).toHaveLength(2)
    })

    test("the Add label button appends a fresh empty row", async () => {
        const wrapper = await mountInput({labels: [{key: "a", value: "1"}]})
        await wrapper.find(".label-input-add").trigger("click")
        expect(wrapper.findAll(".label-input-row")).toHaveLength(2)
    })

    test("removing the last row leaves zero rows", async () => {
        const wrapper = await mountInput({labels: [{key: "a", value: "1"}]})
        await wrapper.find(".label-input-row button").trigger("click")
        expect(wrapper.findAll(".label-input-row")).toHaveLength(0)
    })

    test("locks the key field for existing labels but keeps the value editable", async () => {
        const labels = [{key: "team", value: "data"}]
        const wrapper = await mountInput({labels, existingLabels: labels})
        const inputs = wrapper.find(".label-input-row").findAll("input")
        expect(inputs[0].attributes("disabled")).toBeDefined()
        expect(inputs[1].attributes("disabled")).toBeUndefined()
    })

    test("a newly added row has an editable key", async () => {
        const labels = [{key: "team", value: "data"}]
        const wrapper = await mountInput({labels, existingLabels: labels})
        await wrapper.find(".label-input-add").trigger("click")
        const rows = wrapper.findAll(".label-input-row")
        expect(rows[1].findAll("input")[0].attributes("disabled")).toBeUndefined()
    })

    test("emits update:labels with the new row count when a label is added", async () => {
        const wrapper = await mountInput({labels: [{key: "a", value: "1"}]})
        await wrapper.find(".label-input-add").trigger("click")
        const events = wrapper.emitted("update:labels")
        expect(events).toBeTruthy()
        expect(events![events!.length - 1][0]).toHaveLength(2)
    })
})
