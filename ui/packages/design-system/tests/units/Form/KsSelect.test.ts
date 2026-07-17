import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, nextTick} from "vue"
import {ElSelect} from "element-plus"
import KestraDesignSystem from "../../../src/index"
import KsSelect from "../../../src/components/Form/KsSelect/KsSelect.vue"
import KsOption from "../../../src/components/Form/KsSelect/KsOption.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSelect", () => {
    test("renders trigger with placeholder", () => {
        const wrapper = mount(KsSelect, {
            props: {placeholder: "Select a status"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select").exists()).toBe(true)
        expect(wrapper.find(".kel-select__placeholder").text()).toBe("Select a status")
    })

    test("renders options via KsOption", () => {
        const wrapper = mount(
            defineComponent({
                components: {KsSelect, KsOption},
                template: `<ks-select placeholder="Pick">
                    <ks-option value="A" label="Option A" />
                    <ks-option value="B" label="Option B" />
                </ks-select>`,
            }),
            {global: globalConfig},
        )
        expect(wrapper.find(".kel-select").exists()).toBe(true)
    })

    test("small size applies kel-select--small class", () => {
        const wrapper = mount(KsSelect, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select--small").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsSelect, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select__wrapper.is-disabled").exists()).toBe(true)
    })

    test("multiple mode renders select wrapper", () => {
        const wrapper = mount(KsSelect, {
            props: {multiple: true, placeholder: "Select statuses"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select").exists()).toBe(true)
    })

    test("filterable mode renders input", () => {
        const wrapper = mount(KsSelect, {
            props: {filterable: true, placeholder: "Filter…"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-select").exists()).toBe(true)
    })

    test("loading renders a spinning suffix icon", () => {
        const wrapper = mount(KsSelect, {
            props: {loading: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-icon.is-loading").exists()).toBe(true)
    })

    test("loading drives only the suffix spinner, not ElSelect (dropdown stays usable)", () => {
        const wrapper = mount(KsSelect, {
            props: {loading: true},
            global: globalConfig,
        })
        // `loading` must NOT reach ElSelect — it v-shows the option list on `!loading`,
        // so forwarding would hide still-valid options while they recompute.
        expect(wrapper.findComponent(ElSelect).props("loading")).toBe(false)
    })

    test("no spinner when loading is falsy", () => {
        const wrapper = mount(KsSelect, {
            props: {placeholder: "Idle"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-icon.is-loading").exists()).toBe(false)
    })

    test("colorMap colors the selected value and the dropdown options", async () => {
        const wrapper = mount(
            defineComponent({
                components: {KsSelect, KsOption},
                data: () => ({value: "A"}),
                template: `<ks-select v-model="value" :colorMap="{A: '#ef4444', B: 'var(--ks-text-warning)'}" :teleported="false">
                    <ks-option value="A" label="Option A" />
                    <ks-option value="B" label="Option B" />
                </ks-select>`,
            }),
            {global: globalConfig},
        )
        await nextTick()

        const selected = wrapper.find(".kel-select__placeholder span")
        expect(selected.text()).toBe("Option A")
        expect(selected.attributes("style")).toContain("color: rgb(239, 68, 68)")

        const options = wrapper.findAll(".kel-select-dropdown__item span")
        expect(options).toHaveLength(2)
        expect(options[0].attributes("style")).toContain("color: rgb(239, 68, 68)")
        expect(options[1].attributes("style")).toContain("color: var(--ks-text-warning)")
    })

    test("without colorMap, selected value and options render with no inline color (unaffected)", async () => {
        const wrapper = mount(
            defineComponent({
                components: {KsSelect, KsOption},
                data: () => ({value: "A"}),
                template: `<ks-select v-model="value" :teleported="false">
                    <ks-option value="A" label="Option A" />
                </ks-select>`,
            }),
            {global: globalConfig},
        )
        await nextTick()

        const selected = wrapper.find(".kel-select__placeholder span")
        expect(selected.text()).toBe("Option A")
        expect(selected.attributes("style")).toBeUndefined()

        const option = wrapper.find(".kel-select-dropdown__item span")
        expect(option.text()).toBe("Option A")
        expect(option.attributes("style")).toBeUndefined()
    })
})
