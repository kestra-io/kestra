/// <reference types="vite/client" />
import {ref} from "vue"
import type {I18n} from "vue-i18n"

export const designSystemLocale = ref("en")

export function setDesignSystemLocale(locale: string) {
    designSystemLocale.value = locale
}

const localeModules = import.meta.glob<{default: Record<string, unknown>}>(
    "../components/**/*.json",
)

const LOCALE_FILE_PATTERN = /\.([a-z]{2}(?:_[A-Z]{2})?)\.json$/

// Loads only "en" (the fallback) and the currently active locale, instead of
// eagerly bundling every design-system translation regardless of what the user needs.
export async function registerDesignSystemI18n(i18n: I18n, locale = "en") {
    const localesToLoad = new Set(["en", locale])

    for (const [path, loadMod] of Object.entries(localeModules)) {
        const match = path.match(LOCALE_FILE_PATTERN)
        if (!match || !localesToLoad.has(match[1])) {
            continue
        }

        const mod = await loadMod()
        i18n.global.mergeLocaleMessage(match[1], mod.default)
    }
}
