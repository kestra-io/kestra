/// <reference types="vite/client" />
import {ref} from "vue"
import type {I18n} from "vue-i18n"

import en from "../translations/en.json"

export const designSystemLocale = ref("en")

export function setDesignSystemLocale(locale: string) {
    designSystemLocale.value = locale
}

// English ships with the main bundle — it is what most users run — while every other language is a
// dynamic import, so switching to German costs exactly one extra file. The messages used to live in
// per-component `*.locale.ts` files that each held all thirteen languages: those are static imports
// of the components that use them, so every user downloaded every language inside the eager
// design-system chunk.
const translations = import.meta.glob<{default: Record<string, Record<string, unknown>>}>([
    "../translations/*.json",
    "!../translations/en.json",
])

/**
 * Merges the design-system messages for `locale` into the app's i18n instance.
 *
 * Merged rather than set, so the app's own messages for the same locale survive; call it after the
 * app has loaded its messages, since `setLocaleMessage` replaces a locale wholesale.
 */
export async function registerDesignSystemI18n(i18n: I18n, locale: string = "en") {
    i18n.global.mergeLocaleMessage("en", en.en)

    if (locale === "en") return

    const load = translations[`../translations/${locale}.json`]
    if (!load) return

    const messages = (await load()).default
    i18n.global.mergeLocaleMessage(locale, messages[locale])
}
