import {describe, expect, it} from "vitest"
import {createI18n} from "vue-i18n"

import {registerDesignSystemI18n} from "../../../packages/design-system/src/i18n"
import appEn from "../../../src/translations/en.json"
import appDe from "../../../src/translations/de.json"
import designSystemDe from "../../../packages/design-system/src/translations/de.json"

const setup = () => createI18n<false>({legacy: false, locale: "en", messages: {en: appEn.en}})

describe("registerDesignSystemI18n", () => {
    it("merges the English messages into the app's own", async () => {
        const i18n = setup()

        await registerDesignSystemI18n(i18n)

        // A design-system key, and one the app owns, both resolve from the same instance.
        expect(i18n.global.t("filter.apply")).toBe("Apply filters")
        expect(i18n.global.t("loading")).toBe(appEn.en.loading)
    })

    it("loads nothing but English when the app runs in English", async () => {
        const i18n = setup()

        await registerDesignSystemI18n(i18n, "en")

        // English ships with the main bundle; every other language is a chunk of its own, fetched
        // only when the app runs in it. Eagerly merging them all is what this guards against.
        expect(i18n.global.availableLocales).toEqual(["en"])
    })

    it("merges the requested language after the app has loaded its own messages", async () => {
        const i18n = setup()
        // What init.ts does: setLocaleMessage replaces a locale wholesale, so the design system has
        // to be merged after it — merging first left every design-system key falling back to English.
        i18n.global.setLocaleMessage("de", appDe.de)

        await registerDesignSystemI18n(i18n, "de")
        i18n.global.locale.value = "de"

        expect(i18n.global.t("filter.apply")).toBe(designSystemDe.de.filter.apply)
        expect(i18n.global.t("loading")).toBe(appDe.de.loading)
    })

    it("leaves the app's messages in place for a language it does not ship", async () => {
        const i18n = setup()
        i18n.global.setLocaleMessage("de", appDe.de)

        await registerDesignSystemI18n(i18n, "xx")

        expect(i18n.global.getLocaleMessage("de")).toEqual(appDe.de)
    })
})
