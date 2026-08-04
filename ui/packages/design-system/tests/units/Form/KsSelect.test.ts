import {describe, test, expect, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, ref} from "vue"
import {createI18n} from "vue-i18n"
import {ElSelect} from "element-plus"
import KestraDesignSystem from "../../../src/index"
import KsSelect from "../../../src/components/Form/KsSelect/KsSelect.vue"
import KsOption from "../../../src/components/Form/KsSelect/KsOption.vue"

const i18n = createI18n({legacy: false, locale: "en"})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

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

    describe("selectAll", () => {
        // ElSelect teleports dropdown content to document.body — use document.querySelector
        // for anything inside the dropdown (header, options).
        afterEach(() => {
            document.body.innerHTML = ""
        })

        test("renders select-all header when selectAll and multiple are true", () => {
            mount(KsSelect, {
                props: {selectAll: true, multiple: true},
                global: globalConfig,
            })
            expect(document.querySelector(".kel-select-all-header")).toBeTruthy()
        })

        test("does not render select-all header when selectAll is true but multiple is false", () => {
            mount(KsSelect, {
                props: {selectAll: true, multiple: false},
                global: globalConfig,
            })
            expect(document.querySelector(".kel-select-all-header")).toBeFalsy()
        })

        test("does not render select-all header when multiple is true but selectAll is false", () => {
            mount(KsSelect, {
                props: {multiple: true},
                global: globalConfig,
            })
            expect(document.querySelector(".kel-select-all-header")).toBeFalsy()
        })

        test("clicking select-all button updates model with all visible options", async () => {
            const wrapper = mount(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select v-model="value" :selectAll="true" :multiple="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Beta" />
                        <ks-option value="C" label="Gamma" />
                    </ks-select>`,
                    setup() {
                        return {value: ref([])}
                    },
                }),
                {global: globalConfig},
            )
            const btn = document.querySelector(".kel-select-all-header button") as HTMLButtonElement
            btn.click()
            await wrapper.vm.$nextTick()
            const emitted = wrapper.findComponent(KsSelect).emitted("update:modelValue")
            expect(emitted).toBeTruthy()
            expect(emitted![0][0]).toEqual(expect.arrayContaining(["A", "B", "C"]))
        })

        test("deselect-all button is not shown when model is empty", () => {
            mount(KsSelect, {
                props: {selectAll: true, multiple: true, modelValue: []},
                global: globalConfig,
            })
            const btns = document.querySelectorAll(".kel-select-all-header button")
            expect(btns.length).toBe(1)
        })

        test("deselect-all button shown when model has values, and clears model on click", async () => {
            const wrapper = mount(KsSelect, {
                props: {selectAll: true, multiple: true, modelValue: ["A", "B"]},
                global: globalConfig,
            })
            const btns = document.querySelectorAll(".kel-select-all-header button")
            expect(btns.length).toBe(2)
            ;(btns[1] as HTMLButtonElement).click()
            await wrapper.vm.$nextTick()
            expect(wrapper.emitted("update:modelValue")).toBeTruthy()
            expect(wrapper.emitted("update:modelValue")![0]).toEqual([[]])
        })

        test("custom header slot renders alongside selectAll header", () => {
            mount(
                defineComponent({
                    components: {KsSelect},
                    template: `<ks-select :selectAll="true" :multiple="true">
                        <template #header><span class="custom-header">custom</span></template>
                    </ks-select>`,
                }),
                {global: globalConfig},
            )
            expect(document.querySelector(".kel-select-all-header")).toBeTruthy()
            expect(document.querySelector(".custom-header")).toBeTruthy()
        })

        test("custom header slot renders alone when selectAll is not set", () => {
            mount(
                defineComponent({
                    components: {KsSelect},
                    template: `<ks-select :multiple="true">
                        <template #header><span class="custom-header">custom</span></template>
                    </ks-select>`,
                }),
                {global: globalConfig},
            )
            expect(document.querySelector(".kel-select-all-header")).toBeFalsy()
            expect(document.querySelector(".custom-header")).toBeTruthy()
        })
    })
})
