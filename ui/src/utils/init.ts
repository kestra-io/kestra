import {createRouter, createWebHistory} from "vue-router"
import type {App} from "vue"
import type {RouteRecordRaw} from "vue-router"
import {configure} from "vue-gtag"
import {loadLocaleMessages, setI18nLanguage, setupI18n} from "../translations/i18n"
import moment from "moment-timezone"
// @ts-ignore - moment locale files don't have type declarations
import "moment/dist/locale/de"
// @ts-ignore
import "moment/dist/locale/es"
// @ts-ignore
import "moment/dist/locale/fr"
// @ts-ignore
import "moment/dist/locale/hi"
// @ts-ignore
import "moment/dist/locale/it"
// @ts-ignore
import "moment/dist/locale/ja"
// @ts-ignore
import "moment/dist/locale/ko"
// @ts-ignore
import "moment/dist/locale/pl"
// @ts-ignore
import "moment/dist/locale/pt"
// @ts-ignore
import "moment/dist/locale/ru"
// @ts-ignore
import "moment/dist/locale/zh-cn"
// @ts-ignore
import "moment/dist/locale/pt-br"
import {extendMoment} from "moment-range"
import VueVirtualScroller from "vue-virtual-scroller"
import {createPinia} from "pinia"

import Toast from "./toast"
import filters from "./filters"
import KestraDesignSystem from "@kestra-io/design-system"
import {setDesignSystemLocale, setMomentInstance, setDateFormatter, registerDesignSystemI18n} from "@kestra-io/design-system"
import {date as dateFilter} from "./filters"
import createUnsavedChanged from "./unsavedChange"
import createEventsRouter from "./eventsRouter"
import "./global"
import {useDocStore} from "../stores/doc"


import RouterMd from "../components/utils/RouterMd.vue"
import * as Utils from "./utils"

type GuardFn = (...args: unknown[]) => unknown

export default async (
    app: App,
    routes: RouteRecordRaw[],
    _stores: unknown,
    translations: Record<string, unknown>,
    additionalTranslations: Record<string, unknown> = {},
    guards: Record<string, GuardFn | undefined> = {},
) => {
    // router
    const router = createRouter({
        // make e2e tests pass in dev mode
        history: createWebHistory(import.meta.env.DEV ? "/ui" : window.KESTRA_UI_PATH),
        routes,
    })

    const piniaStore = createPinia()
    app.use(piniaStore)

    /**
     * Manage docId initialization for Contextual docs
     */
    router.beforeEach((to, from) => {
        // set the docId from the path
        // so it has a default
        const pathArray = to.path.split("/")
        const docId = pathArray[pathArray.length-1]

        const docStore = useDocStore()
        docStore.docId = docId

        // propagate showDocId query param
        // to the next page to facilitate docs binding
        if(to.query["showDocId"] === undefined && from.query["showDocId"] !== undefined){
            return {path: to.path, query: {...to.query, showDocId: from.query["showDocId"]}}
        }
    })

    if(guards.beforeEach){
        router.beforeEach(guards.beforeEach.bind(null, router) as Parameters<typeof router.beforeEach>[0])
    }

    if(guards.beforeResolve){
        router.beforeResolve(guards.beforeResolve.bind(null, router) as Parameters<typeof router.beforeResolve>[0])
    }

    if(guards.afterEach){
        router.afterEach(guards.afterEach.bind(null, router) as Parameters<typeof router.afterEach>[0])
    }

    router.afterEach((to) => {
        window.dispatchEvent(new CustomEvent("KestraRouterAfterEach", to as unknown as CustomEventInit))
    })

    // avoid loading router in storybook
    // as it conflicts with storybook's
    if(routes.length){
        app.use(router)
    }

    // Google Analytics
    if (window.KESTRA_GOOGLE_ANALYTICS !== null) {
        configure({
            tagId: window.KESTRA_GOOGLE_ANALYTICS,
        })
    }

    // l18n
    const locale = Utils.getLang()

    // FIXME: any - setupI18n options type doesn't expose all options
    const i18n = setupI18n({
        locale: "en",
        messages: translations,
        allowComposition: true,
        legacy: false,
        warnHtmlMessage: false,
    } as any) // FIXME: any

    // Merge design-system locales before first render, so parent computeds
    // that call t() on design-system keys don't cache the raw key.
    await registerDesignSystemI18n(i18n)

    if(locale !== "en"){
        // FIXME: any - loadLocaleMessages/setI18nLanguage expect literal locale types
        await loadLocaleMessages(i18n, locale as any, additionalTranslations as any) // FIXME: any
        await setI18nLanguage(i18n, locale as any) // FIXME: any
    }
    setDesignSystemLocale(locale)
    app.use(i18n)

    // moment
    moment.locale(locale)
    const momentExtended = extendMoment(moment)
    app.config.globalProperties.$moment = momentExtended
    setMomentInstance(momentExtended)
    setDateFormatter(dateFilter as any) // FIXME: any - dateFilter signature differs from DateFormatterFn

    // others plugins
    app.use(Toast)
    app.provide("Toast", Toast)
    app.use(VueVirtualScroller)

    // filters
    app.config.globalProperties.$filters = filters

    // kestra design system (registers KsSelect, etc. globally)
    app.use(KestraDesignSystem)

    // navigation guard
    createUnsavedChanged(app, router)
    createEventsRouter(app, router)

    app.component("RouterMd", RouterMd)

    app.config.globalProperties.append = (path: string, pathToAppend: string) => path + (path.endsWith("/") ? "" : "/") + pathToAppend

    return {router, piniaStore}
}
