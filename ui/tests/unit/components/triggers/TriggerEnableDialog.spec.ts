import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import TriggerEnableDialog from "../../../../src/components/triggers/TriggerEnableDialog.vue"
import en from "../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", messages: en})

const stubs = {
    KsDialog: {template: "<div><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>"},
    KsAlert: {template: "<div><slot /></div>"},
    KsRadioGroup: {template: "<div><slot /></div>"},
    KsRadio: {props: ["value"], template: "<label><slot /></label>"},
    KsButton: {template: "<button><slot /></button>"},
}

describe("TriggerEnableDialog", () => {
    it("renders the trigger property of the follow option as a code element, not as escaped markup", () => {
        const wrapper = mount(TriggerEnableDialog, {props: {modelValue: true}, global: {plugins: [i18n], stubs}})

        const follow = wrapper.findAll("label")[1]
        expect(follow.text()).toBe("Follow the trigger's recoverMissedSchedules configuration")
        expect(follow.find("code").text()).toBe("recoverMissedSchedules")
    })
})
