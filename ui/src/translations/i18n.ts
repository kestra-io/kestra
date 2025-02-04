import {nextTick} from "vue"
import {createI18n, type I18n} from "vue-i18n"

const translations = import.meta.glob("./*.json")

export const SUPPORT_LOCALES = ["de","en","es","fr","hi","it","ja","ko","pl","pt","ru","zh_CN"] as const

export function setupI18n(options: {locale: (typeof SUPPORT_LOCALES)[number]} = {locale: "en"}) {
  const i18n = createI18n(options)
  setI18nLanguage(i18n, options.locale)
  return i18n
}

export function setI18nLanguage(i18n: I18n, locale: (typeof SUPPORT_LOCALES)[number]) {
  if (i18n.mode === "legacy") {
    i18n.global.locale = locale
  } else {
    // @ts-expect-error vue-i18n is not typed correcly it seems
    i18n.global.locale.value = locale
  }
  /**
   * NOTE:
   * If you need to specify the language setting for headers, such as the `fetch` API, set it here.
   * The following is an example for axios.
   *
   * axios.defaults.headers.common['Accept-Language'] = locale
   */
  document.querySelector("html")?.setAttribute("lang", locale)
}

export async function loadLocaleMessages(i18n: I18n, locale: (typeof SUPPORT_LOCALES)[number], additionalTranslationsProvider: Record<string, () => Promise<any>>) {
  const messages = {default: {}} as any
  
  if(additionalTranslationsProvider[locale]){
    // load additional translations from the provider
    const additionalTranslations = await additionalTranslationsProvider[locale]()
    messages.default[locale] = additionalTranslations.default[locale]
  }else{
    // load locale messages with dynamic import
    messages.default[locale] = await translations[`./${locale}.json`]() as any
  }

  // set locale and locale message
  i18n.global.setLocaleMessage(locale, messages.default[locale])

  return nextTick()
}