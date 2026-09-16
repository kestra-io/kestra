import {describe, expect, it} from "vitest"
import {createI18n} from "vue-i18n"
import {splitTranslation} from "../../../src/utils/splitTranslation"
import en from "../../../src/translations/en.json"

const messages = {
    en: {
        ...en.en,
        test: {
            middle: "Searching {namespace} now",
            trailing: "Searching {namespace}",
            noSlot: "Nothing to interpolate",
            twice: "{namespace} is not {namespace}",
        },
    },
}
const {t} = createI18n({legacy: false, locale: "en", messages}).global

describe("splitTranslation", () => {
    it("returns the text on each side of the slot", () => {
        expect(splitTranslation(t, "test.middle", "namespace")).toEqual(["Searching ", " now"])
        expect(splitTranslation(t, "test.trailing", "namespace")).toEqual(["Searching ", ""])
    })

    it("interpolates the other named arguments of the message", () => {
        expect(splitTranslation(t, "ai.copilot.contextRemoved", "id", {type: "flow"}))
            .toEqual(["Removed flow ", " from context."])
    })

    it("puts the whole message before the slot when the key holds no placeholder", () => {
        expect(splitTranslation(t, "test.noSlot", "namespace")).toEqual(["Nothing to interpolate", ""])
    })

    it("keeps every word when the placeholder appears twice, rendering the slot once", () => {
        expect(splitTranslation(t, "test.twice", "namespace")).toEqual(["", " is not "])
    })
})
