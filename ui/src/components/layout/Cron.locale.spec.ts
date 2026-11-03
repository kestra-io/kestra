import {afterEach, describe, expect, test, vi} from "vitest"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {storageKeys} from "../../utils/constants"

import Cron from "./Cron.vue"

const sentence = (lang: string) => {
    localStorage.setItem(storageKeys.LANG, lang)
    return i18nMount(Cron, {props: {cronExpression: "0 9 * * 1"}}).text()
}

describe("Cron", () => {
    afterEach(() => {
        localStorage.removeItem(storageKeys.LANG)
        vi.restoreAllMocks()
    })

    test.each([
        ["fr", "lundi"],
        ["de", "Montag"],
        ["es", "lunes"],
        ["it", "lunedì"],
        ["pt_BR", "segunda"],
        ["pt", "segunda"],
        ["ru", "понедельник"],
    ])("describes the expression in the UI language (%s)", (lang, monday) => {
        expect(sentence(lang)).toContain(monday)
    })

    test("uses a translated sentence for every UI language cronstrue ships", () => {
        const english = sentence("en")
        for (const lang of ["ja", "ko", "pl", "zh_CN"]) {
            expect(sentence(lang), lang).not.toBe(english)
        }
    })

    test("falls back to English without a warning for a language cronstrue doesn't ship", () => {
        const warn = vi.spyOn(console, "warn")

        expect(sentence("hi")).toBe(sentence("en"))
        expect(warn).not.toHaveBeenCalled()
    })
})
