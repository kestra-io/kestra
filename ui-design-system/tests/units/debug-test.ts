import {describe, test} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../src/index"
import KsAutocomplete from "../../src/components/Form/KsAutocomplete.vue"
import KsTimePicker from "../../src/components/Form/KsTimePicker.vue"

const globalConfig = {plugins: [KestraDesignSystem]}
const fetchSuggestions = (_query: string, callback: (results: {value: string}[]) => void) => {
    callback([{value: "alpha"}])
}

describe("debug", () => {
    test("autocomplete disabled html", () => {
        const wrapper = mount(KsAutocomplete, {
            props: {disabled: true, fetchSuggestions},
            global: globalConfig,
        })
        console.log("AUTOCOMPLETE DISABLED HTML:", wrapper.html())
    })

    test("autocomplete clearable html", () => {
        const wrapper = mount(KsAutocomplete, {
            props: {modelValue: "alpha", clearable: true, fetchSuggestions},
            global: globalConfig,
        })
        console.log("AUTOCOMPLETE CLEARABLE HTML:", wrapper.html())
    })

    test("timepicker small html", () => {
        const wrapper = mount(KsTimePicker, {
            props: {size: "small"},
            global: globalConfig,
        })
        console.log("TIMEPICKER SMALL HTML:", wrapper.html())
    })
})
