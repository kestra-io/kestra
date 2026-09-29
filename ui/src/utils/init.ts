import {createRouter, createWebHistory} from "vue-router"
import type {App} from "vue"
import type {RouteRecordRaw} from "vue-router"
import {configure} from "vue-gtag"
import {loadLocaleMessages, setI18nLanguage, setupI18n} from "../translations/i18n"
import VueVirtualScroller from "vue-virtual-scroller"
import {createPinia} from "pinia"

import Toast from "./toast"
import filters from "./filters"
import KestraDesignSystem from "@kestra-io/design-system"
import {setDesignSystemLocale, dateUtils, registerDesignSystemI18n} from "@kestra-io/design-system"
import createUnsavedChanged from "./unsavedChange"
import createEventsRouter from "./eventsRouter"
import "./global"
import {useDocStore} from "../stores/doc"
import {entityNotFoundGuard} from "./routeEntityGuard"


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

    // After the edition's own guards, so an auth or tenant redirect wins over probing an entity
    // the user is not going to be shown anyway.
    router.beforeResolve(entityNotFoundGuard)

    if(guards.afterEach){
        router.afterEach(guards.afterEach.bind(null, router) as Parameters<typeof router.afterEach>[0])
    }

    router.afterEach((to) => {
        window.dispatchEvent(new CustomEvent("KestraRouterAfterEach", to as unknown as CustomEventInit))
    })

    // Registered before the router installs: app.use(router) starts the first navigation, and both
    // beforeEach and afterEach hooks added after any of the awaits below are missed by it.
    createUnsavedChanged(app, router)
    createEventsRouter(app, router)

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
        await loadLocaleMessages(i18n, locale, additionalTranslations as any) // FIXME: additional translations provider lacks its module type
        await setI18nLanguage(i18n, locale)
    }
    setDesignSystemLocale(locale)
    app.use(i18n)

    await dateUtils.setLocale(locale)

    // others plugins
    app.use(Toast)
    app.provide("Toast", Toast)
    app.use(VueVirtualScroller)

    // filters
    app.config.globalProperties.$filters = filters

    // kestra design system (registers KsSelect, etc. globally)
    app.use(KestraDesignSystem)

    app.component("RouterMd", RouterMd)

    app.config.globalProperties.append = (path: string, pathToAppend: string) => path + (path.endsWith("/") ? "" : "/") + pathToAppend

    return {router, piniaStore}
}
