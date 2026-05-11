/// <reference types="vite/client" />
import {ref} from "vue"
import type {I18n} from "vue-i18n"

export const designSystemLocale = ref("en")

export function setDesignSystemLocale(locale: string) {
    designSystemLocale.value = locale
}

const localeModules = import.meta.glob<{default: Record<string, object>}>(
    "../components/**/*.locale.ts",
    {eager: true},
)

export function registerDesignSystemI18n(i18n: I18n) {
    for (const mod of Object.values(localeModules)) {
        for (const [lang, messages] of Object.entries(mod.default)) {
            i18n.global.mergeLocaleMessage(lang, messages)
        }
    }
}
