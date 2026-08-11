/// <reference types="vite/client" />
import {ref} from "vue"
import type {I18n} from "vue-i18n"

export const designSystemLocale = ref("en")

export function setDesignSystemLocale(locale: string) {
    designSystemLocale.value = locale
}

const localeModules = import.meta.glob<{default: Record<string, object>}>(
    "../components/**/*.locale.ts",
)

export async function registerDesignSystemI18n(i18n: I18n) {
    for (const loadMod of Object.values(localeModules)) {
        const mod = await loadMod()
        for (const [lang, messages] of Object.entries(mod.default)) {
            i18n.global.mergeLocaleMessage(lang, messages)
        }
    }
}
