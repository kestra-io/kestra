import {nextTick, ref} from "vue"
import {createI18n, type I18n} from "vue-i18n"

const translations = import.meta.glob(["./*.json", "!./en.json"])

export const SUPPORT_LOCALES = ["de","en","es","fr","hi","it","ja","ko","pl","pt","pt_BR","ru","zh_CN"] as const
type Locales = (typeof SUPPORT_LOCALES)[number]

export const globalI18n = ref<I18n<any, any, any, Locales, false>["global"]>()

export function setupI18n(options: {locale: Locales} = {locale: "en"}) {
  const i18n = createI18n<false>(options)
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
  document.querySelector("html")?.setAttribute("lang", locale)
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