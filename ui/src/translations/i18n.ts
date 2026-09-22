import {nextTick, ref} from "vue"
import {createI18n, type I18n} from "vue-i18n"

const translations = import.meta.glob(["./*.json", "!./en.json"])

import {SUPPORT_LOCALES} from "./languages"

type Locales = (typeof SUPPORT_LOCALES)[number]

export const globalI18n = ref<I18n<any, any, any, Locales, false>["global"]>()

/**
 * What happens when `t()` is asked for a key no locale defines.
 *
 * - `throw`: the default under Vitest, so a component that renders a raw key fails its test.
 * - `report`: the default everywhere else, including production builds. One `console.error` per
 *   key, which is what the e2e suite listens for; the render itself still shows the key, as before.
 * - `silent`: for Storybook, where isolated stories legitimately lack most namespaced keys.
 */
export type MissingKeyPolicy = "throw" | "report" | "silent"

export const MISSING_KEY_MESSAGE = "[i18n] Missing translation key"

let missingKeyPolicy: MissingKeyPolicy = import.meta.env.MODE === "test" ? "throw" : "report"
const reportedMissingKeys = new Set<string>()

export function setMissingKeyPolicy(policy: MissingKeyPolicy) {
  missingKeyPolicy = policy
}

/**
 * vue-i18n calls this once per locale in the fallback chain, so a key present in English but
 * missing in the active locale reaches it too. Only the English miss is a bug: English is the
 * source every locale is generated from, so a key absent there is absent everywhere.
 */
function onMissingKey(locale: string, key: string): void {
  if (missingKeyPolicy === "silent" || locale !== "en") return

  const message = `${MISSING_KEY_MESSAGE} "${key}" - it renders as its raw id`
  if (missingKeyPolicy === "throw") throw new Error(message)
  if (reportedMissingKeys.has(key)) return
  reportedMissingKeys.add(key)
  console.error(message)
}

/**
 * Plural selection for locales that vue-i18n's default rule gets wrong.
 *
 * Given three forms, the default rule picks index 1 for n === 1 and index 2 for
 * everything else, which is the [zero, one, other] shape. Polish is [one, few, many]:
 * 1, then 2-4, then 5+ with 12-14 as an exception. Without this rule a three-form
 * Polish message renders "1 pliki" and "2 plików" — both wrong, and the first is the
 * most common case.
 *
 * Russian has the same three-form structure and the same bug, but its messages have not
 * been reviewed against a correct rule, so it deliberately stays on the default.
 *
 * @param choice the count being pluralised
 * @param choicesLength how many forms the message declares
 */
function polishPluralIndex(choice: number, choicesLength: number): number {
  if (choicesLength < 3) return choice === 1 ? 0 : 1

  const n = Math.abs(choice)
  if (n === 1) return 0

  const mod10 = n % 10
  const mod100 = n % 100
  // 2-4, 22-24, 32-34 … take the "few" form; 12-14 fall through to "many".
  if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return 1

  return 2
}

export function setupI18n(options: {locale: Locales} = {locale: "en"}) {
  const i18n = createI18n<false>({...options, pluralRules: {pl: polishPluralIndex}, missing: onMissingKey, missingWarn: false})
  setI18nLanguage(i18n, options.locale)
  globalI18n.value = i18n.global
  return i18n
}

export function setI18nLanguage(i18n: I18n, locale: (typeof SUPPORT_LOCALES)[number]) {
  if (i18n.mode === "legacy") {
    i18n.global.locale = locale
  } else {
    // @ts-expect-error vue-i18n is not typed correctly it seems
    i18n.global.locale.value = locale
  }
  /**
   * NOTE:
   * If you need to specify the language setting for headers, such as the `fetch` API, set it here.
   * The following is an example for axios.
   *
   * axios.defaults.headers.common['Accept-Language'] = locale
   */
  // The html lang attribute must be a BCP 47 tag, so the underscore codes ("pt_BR") get their hyphen form.
  document.querySelector("html")?.setAttribute("lang", locale.replace(/_/g, "-"))
}

export async function loadLocaleMessages(i18n: I18n, locale: (typeof SUPPORT_LOCALES)[number], additionalTranslationsProvider: Record<string, () => Promise<any>>) {
  let messages = {} as any

  if(additionalTranslationsProvider[locale]){
    // load additional translations from the provider
    const additionalTranslations = await additionalTranslationsProvider[locale]()
    messages = additionalTranslations.default
  }else{
    // load locale messages with dynamic import
    messages = await translations[`./${locale}.json`]()
  }

  // set locale and locale message
  i18n.global.setLocaleMessage(locale, messages[locale])

  return nextTick()
}