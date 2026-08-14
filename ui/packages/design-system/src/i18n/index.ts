/// <reference types="vite/client" />
import {ref} from "vue"
import type {I18n} from "vue-i18n"

export const designSystemLocale = ref("en")

export function setDesignSystemLocale(locale: string) {
    designSystemLocale.value = locale
}

// Eager: a lazy glob makes every locale file its own dynamic-import boundary, so the bundler
// emits one chunk per file and the app fetches them serially before it can render anything.
const localeModules = import.meta.glob<{default: Record<string, object>}>(
    "../components/**/*.locale.ts",
    {eager: true},
)

/** Merges the component locale files into the app messages. Synchronous, so callers cannot
 * render before the design-system keys exist. */
export function registerDesignSystemI18n(i18n: I18n) {
    for (const mod of Object.values(localeModules)) {
        for (const [lang, messages] of Object.entries(mod.default)) {
            i18n.global.mergeLocaleMessage(lang, messages)
        }
    }
}
