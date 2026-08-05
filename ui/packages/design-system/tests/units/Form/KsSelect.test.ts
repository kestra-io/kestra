import {describe, test, expect, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, nextTick, ref} from "vue"
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

        /**
         * The button's visibility derives from ElSelect's registered options, reached through a
         * template ref that is only populated after the first render — so it appears one tick in.
         */
        const mountAndSettle = async (component: Parameters<typeof mount>[0]) => {
            const wrapper = mount(component, {global: globalConfig})
            await nextTick()
            return wrapper
        }

        test("renders select-all button when selectAll and multiple are true", async () => {
            await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :selectAll="true" :multiple="true">
                        <ks-option value="A" label="Alpha" />
                    </ks-select>`,
                }),
            )
            expect(document.querySelector(".kel-select-all-btn")).toBeTruthy()
        })

        test("does not render select-all button when there are no options", () => {
            mount(KsSelect, {
                props: {selectAll: true, multiple: true},
                global: globalConfig,
            })
            expect(document.querySelector(".kel-select-all-btn")).toBeFalsy()
        })

        test("does not render select-all button when the filter matches no option", async () => {
            const wrapper = await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :selectAll="true" :multiple="true" :filterable="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Beta" />
                    </ks-select>`,
                }),
            )
            expect(document.querySelector(".kel-select-all-btn")).toBeTruthy()
            const elSelect = wrapper.findComponent(ElSelect).vm as unknown as {states: {inputValue: string}}
            elSelect.states.inputValue = "zzz"
            await nextTick()
            await nextTick()
            expect(document.querySelector(".kel-select-all-btn")).toBeFalsy()
        })

        test("does not render select-all button when selectAll is true but multiple is false", () => {
            mount(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :selectAll="true" :multiple="false">
                        <ks-option value="A" label="Alpha" />
                    </ks-select>`,
                }),
                {global: globalConfig},
            )
            expect(document.querySelector(".kel-select-all-btn")).toBeFalsy()
        })

        test("does not render select-all button when multiple is true but selectAll is false", () => {
            mount(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :multiple="true">
                        <ks-option value="A" label="Alpha" />
                    </ks-select>`,
                }),
                {global: globalConfig},
            )
            expect(document.querySelector(".kel-select-all-btn")).toBeFalsy()
        })

        test("clicking select-all button updates model with all visible options", async () => {
            const wrapper = await mountAndSettle(
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
            )
            const btn = document.querySelector(".kel-select-all-btn") as HTMLButtonElement
            btn.click()
            await nextTick()
            const emitted = wrapper.findComponent(KsSelect).emitted("update:modelValue")
            expect(emitted).toBeTruthy()
            expect(emitted![0][0]).toEqual(["A", "B", "C"])
        })

        test("clicking select-all button closes the dropdown", async () => {
            const wrapper = await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select v-model="value" :selectAll="true" :multiple="true">
                        <ks-option value="A" label="Alpha" />
                    </ks-select>`,
                    setup() {
                        return {value: ref([])}
                    },
                }),
            )
            const elSelect = wrapper.findComponent(ElSelect).vm as unknown as {expanded: boolean}
            ;(document.querySelector(".kel-select-all-btn") as HTMLButtonElement).click()
            await nextTick()
            expect(elSelect.expanded).toBe(false)
        })

        test("select-all reports unchecked when nothing is selected", async () => {
            await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :modelValue="[]" :selectAll="true" :multiple="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Beta" />
                    </ks-select>`,
                }),
            )
            expect(document.querySelector(".kel-select-all-btn")?.getAttribute("aria-checked")).toBe("false")
        })

        test("select-all reports mixed when only some options are selected", async () => {
            await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :modelValue="['A']" :selectAll="true" :multiple="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Beta" />
                    </ks-select>`,
                }),
            )
            expect(document.querySelector(".kel-select-all-btn")?.getAttribute("aria-checked")).toBe("mixed")
        })

        test("select-all reports checked when every option is selected", async () => {
            await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :modelValue="['A', 'B']" :selectAll="true" :multiple="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Beta" />
                    </ks-select>`,
                }),
            )
            expect(document.querySelector(".kel-select-all-btn")?.getAttribute("aria-checked")).toBe("true")
        })

        test("clicking select-all while fully selected deselects every visible option", async () => {
            const wrapper = await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select v-model="value" :selectAll="true" :multiple="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Beta" />
                    </ks-select>`,
                    setup() {
                        return {value: ref(["A", "B"])}
                    },
                }),
            )
            ;(document.querySelector(".kel-select-all-btn") as HTMLButtonElement).click()
            await nextTick()
            const emitted = wrapper.findComponent(KsSelect).emitted("update:modelValue")
            expect(emitted).toBeTruthy()
            expect(emitted!.at(-1)![0]).toEqual([])
        })

        test("deselecting a filtered subset keeps selections outside the filter", async () => {
            const wrapper = await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select v-model="value" :selectAll="true" :multiple="true" :filterable="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Alpine" />
                        <ks-option value="C" label="Beta" />
                    </ks-select>`,
                    setup() {
                        return {value: ref(["A", "B", "C"])}
                    },
                }),
            )
            const elSelect = wrapper.findComponent(ElSelect).vm as unknown as {states: {inputValue: string}}
            elSelect.states.inputValue = "alp"
            await nextTick()
            await nextTick()
            ;(document.querySelector(".kel-select-all-btn") as HTMLButtonElement).click()
            await nextTick()
            const emitted = wrapper.findComponent(KsSelect).emitted("update:modelValue")
            expect(emitted!.at(-1)![0]).toEqual(["C"])
        })

        test("select-all button only selects options matching the active filter", async () => {
            const wrapper = await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select v-model="value" :selectAll="true" :multiple="true" :filterable="true">
                        <ks-option value="A" label="Alpha" />
                        <ks-option value="B" label="Alpine" />
                        <ks-option value="C" label="Beta" />
                    </ks-select>`,
                    setup() {
                        return {value: ref([])}
                    },
                }),
            )
            // Setting the query the way ElSelect's own input handler does; a watchEffect then
            // re-runs each option's real filter predicate and updates its `visible` flag.
            const elSelect = wrapper.findComponent(ElSelect).vm as unknown as {states: {inputValue: string}}
            elSelect.states.inputValue = "alp"
            await nextTick()
            await nextTick()
            ;(document.querySelector(".kel-select-all-btn") as HTMLButtonElement).click()
            await nextTick()
            const emitted = wrapper.findComponent(KsSelect).emitted("update:modelValue")
            expect(emitted).toBeTruthy()
            // "Alpha" and "Alpine" match "alp"; "Beta" does not.
            expect(emitted!.at(-1)![0]).toEqual(["A", "B"])
        })

        test("custom header slot renders alongside select-all button", async () => {
            await mountAndSettle(
                defineComponent({
                    components: {KsSelect, KsOption},
                    template: `<ks-select :selectAll="true" :multiple="true">
                        <template #header><span class="custom-header">custom</span></template>
                        <ks-option value="A" label="Alpha" />
                    </ks-select>`,
                }),
            )
            expect(document.querySelector(".kel-select-all-btn")).toBeTruthy()
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
            expect(document.querySelector(".kel-select-all-btn")).toBeFalsy()
            expect(document.querySelector(".custom-header")).toBeTruthy()
        })
    })
})
