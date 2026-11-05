import {describe, it, expect, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KeyShortcuts from "./KeyShortcuts.vue"
import {useKeyShortcuts} from "../../utils/useKeyShortcuts"
import en from "../../translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", messages: {en}})

const stubs = {
    KsDialog: {template: "<div class=\"dialog\"><slot name=\"header\" /><slot /></div>"},
    KsIcon: {template: "<span><slot /></span>"},
    KsTag: {template: "<span class=\"tag\"><slot /></span>"},
}

describe("KeyShortcuts", () => {
    beforeEach(() => {
        const {hideKeyShortcuts} = useKeyShortcuts()
        hideKeyShortcuts()
    })

    it("renders the header and all 14 keyboard shortcut commands", () => {
        const {showKeyShortcuts} = useKeyShortcuts()
        showKeyShortcuts()

        const wrapper = mount(KeyShortcuts, {global: {plugins: [i18n], stubs}})

        expect(wrapper.find(".header-title").exists()).toBe(true)
        expect(wrapper.find(".commands-list").exists()).toBe(true)

        const rows = wrapper.findAll(".command-row")
        expect(rows.length).toBe(14)

        const firstRow = rows[0]
        expect(firstRow.find(".keys").exists()).toBe(true)
        expect(firstRow.find(".command-description").exists()).toBe(true)
        expect(firstRow.findAll(".tag").length).toBe(2)
    })
})
