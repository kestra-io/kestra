import {describe, expect, it} from "vitest"
import {createApp} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {designSystemI18nReady} from "@kestra-io/design-system"

describe("designSystemI18nReady", () => {
    it("should resolve once install has merged the design system's own messages", async () => {
        const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})
        const app = createApp({template: "<div />"})
        app.use(i18n)
        app.use(KestraDesignSystem)

        await designSystemI18nReady()

        expect(i18n.global.getLocaleMessage("en")).toHaveProperty("ks_password.show")
    })
})
