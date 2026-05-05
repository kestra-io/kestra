import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsAutocomplete from "../../../src/components/Form/KsAutocomplete.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const fetchSuggestions = (query: string, callback: (results: {value: string}[]) => void) => {
    callback([{value: "alpha"}, {value: "beta"}, {value: "gamma"}].filter(s => s.value.includes(query)))
}

describe("KsAutocomplete", () => {
    test("renders autocomplete element", () => {
        const wrapper = mount(KsAutocomplete, {
            props: {fetchSuggestions},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-autocomplete").exists()).toBe(true)
    })

    test("placeholder prop is forwarded to input", () => {
        const wrapper = mount(KsAutocomplete, {
            props: {placeholder: "Search…", fetchSuggestions},
            global: globalConfig,
        })
        expect(wrapper.find("input").attributes("placeholder")).toBe("Search…")
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsAutocomplete, {
            props: {disabled: true, fetchSuggestions},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input.is-disabled").exists()).toBe(true)
    })

    test("clearable renders suffix indicator when value present", async () => {
        const wrapper = mount(KsAutocomplete, {
            props: {modelValue: "alpha", clearable: true, fetchSuggestions},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input--suffix").exists()).toBe(true)
    })
})
