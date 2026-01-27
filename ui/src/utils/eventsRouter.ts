import {nextTick} from "vue"
import _isEqual from "lodash/isEqual"
import type {RouteLocationNormalized} from "vue-router"
import {useApiStore} from "../stores/api"

interface PageInfo {
    origin: string
    path: string
    fullPath: string
    params: { key: string, value: string }[]
    queries: { key: string, values: string[] }[]
    name: string | undefined
    hash?: string
}

export const pageFromRoute = (route: RouteLocationNormalized): PageInfo => {
    return {
        origin: window.location.origin,
        path: route.path,
        fullPath: route.fullPath,
        params: Object.keys(route.params)
            .map((key) => ({key, value: route.params[key] as string})),
        queries: Object.keys(route.query)
            .map((key) => {
                return {key, values: (route.query[key] instanceof Array ? route.query[key] as string[] : [route.query[key] as string])}
            }),
        name: route.name as string | undefined,
        hash: route.hash !== "" ? route.hash : undefined,
    }
}

export default (_app: any, router: any) => {
    const apiStore = useApiStore()
    router.afterEach((to: RouteLocationNormalized, from: RouteLocationNormalized) => {
        nextTick().then(() => {
            if (_isEqual(from, to)) {
                return
            }
            const currentOrigin = window.location.origin
            const isInitialNavigation = (from.matched?.length ?? 0) === 0
            const referrerUrl = isInitialNavigation
                ? (document.referrer || undefined)
                : `${currentOrigin}${from.fullPath}`
            const referringDomain = (() => {
                if (!referrerUrl) return undefined
                try {
                    return new URL(referrerUrl).host
                } catch {
                    return undefined
                }
            })()

            apiStore.events({
                type: "PAGE",
                page: pageFromRoute(to),
                $referrer: referrerUrl,
                $referring_domain: referringDomain,
            })
        })
    })
}
