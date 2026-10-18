import {shallowReactive} from "vue"
import type {Language} from "element-plus/es/locale"
import en from "element-plus/es/locale/lang/en"

const ENGLISH: Language = {
    ...en,
    el: {
        ...en.el,
        pagination: {
            ...en.el.pagination,
            pagesize: " per page",
        },
    },
}

// "en" ships with the component; every other locale is a separate chunk fetched only when selected.
const LOCALE_LOADERS: Record<string, () => Promise<{default: Language}>> = {
    de: () => import("element-plus/es/locale/lang/de"),
    es: () => import("element-plus/es/locale/lang/es"),
    fr: () => import("element-plus/es/locale/lang/fr"),
    hi: () => import("element-plus/es/locale/lang/hi"),
    it: () => import("element-plus/es/locale/lang/it"),
    ja: () => import("element-plus/es/locale/lang/ja"),
    ko: () => import("element-plus/es/locale/lang/ko"),
    pl: () => import("element-plus/es/locale/lang/pl"),
    pt: () => import("element-plus/es/locale/lang/pt"),
    "pt-br": () => import("element-plus/es/locale/lang/pt-br"),
    ru: () => import("element-plus/es/locale/lang/ru"),
    "zh-cn": () => import("element-plus/es/locale/lang/zh-cn"),
}

const loaded = shallowReactive<Record<string, Language>>({})
const pending = new Map<string, Promise<void>>()

/** Normalises Kestra's codes ("pt_BR") to Element Plus's file names ("pt-br"). */
const localeKey = (lang: string) => lang.toLowerCase().replace(/_/g, "-")

export function loadPaginationLocale(lang: string): Promise<void> {
    const key = localeKey(lang)
    const loader = LOCALE_LOADERS[key]
    if (!loader || loaded[key]) return Promise.resolve()

    let load = pending.get(key)
    if (!load) {
        load = loader()
            .then((mod) => {
                loaded[key] = mod.default
            })
            .finally(() => pending.delete(key))
        pending.set(key, load)
    }
    return load
}

/** The Element Plus locale for a language, in English until that language has loaded. */
export function paginationLocaleFor(lang: string): Language {
    return loaded[localeKey(lang)] ?? ENGLISH
}
