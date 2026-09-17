import type {Router, RouteLocationNormalized, RouteLocationRaw, RouteLocationNamedRaw} from "vue-router"
import type {App} from "vue"

import {storageKeys} from "../utils/constants"

/** Exact rather than a substring test: `:tenantId` declares a different param. */
const TENANT_PARAM = /:tenant(?![A-Za-z0-9_])/

const DEFAULT_TENANT = "main"

/** What these helpers read off a location, so a `resolve()` result is accepted alongside a guard's
 *  `to`: the two differ only in whether `name` can be null, which neither of them looks at. */
type TenantLocation = Pick<RouteLocationNormalized, "path" | "params" | "query" | "hash" | "meta"> & {
    matched: readonly {path: string}[]
}

/** The tenant to assume before any route has been matched - the only thing available to a record's
 *  own `redirect`, which vue-router resolves through its internal closure where no guard can reach. */
export const rememberedTenant = (): string => {
    try {
        return localStorage.getItem(storageKeys.SELECTED_TENANT) || DEFAULT_TENANT
    } catch {
        return DEFAULT_TENANT
    }
}

/** Nothing matched, or only the catch-all did - i.e. a URL whose first segment is not a tenant.
 *  A single-segment URL such as `/flows` matches no record at all now that `:tenant` is required,
 *  so the empty case is not merely defensive. */
export const isTenantLessEntry = (route: TenantLocation): boolean =>
    route.matched.length === 0 || route.matched.some((record) => record.path.includes(":pathMatch"))

/** Rewrites a tenant-less path to the current tenant, defaulting to "main". Must be registered through
 *  `initApp`: a guard registered after one of its awaits is missed by the router's first navigation. */
export function tenantGuard(router: Router, to: TenantLocation, from: TenantLocation | undefined): boolean | RouteLocationRaw {
    // on login, prevent redirection to tenant
    if (to.meta?.anonymous === true) {
        return true
    }
    if (to.path !== "/" && !to.params.tenant) {
        // Use current tenant from route context, fallback to "main"
        const currentTenant = (from?.params?.tenant as string) || DEFAULT_TENANT
        // No `replace` here: vue-router forces one on the first navigation, keeping the tenant-less URL
        // out of history, and setting it explicitly would turn a caller's `router.replace()` into a `push`.
        return {path: `/${currentTenant}${to.path}`, query: to.query, hash: to.hash}
    }

    return tenantLessDeepLink(router, to, from) ?? true
}

/** Where a URL whose first segment turned out not to be a tenant should go instead, or `undefined`
 *  when it is fine as it is.
 *
 *  Every route declares `:tenant` as a required param, so the first segment of a URL is always the
 *  tenant and never a section name - that is what keeps `/assets/flows` the Flows page of a tenant
 *  called `assets`. The cost is that a tenant-less deep link like `/assets/<assetId>` matches nothing,
 *  so a 404 is retried under the current tenant. Only a retry that resolves somewhere real is taken,
 *  which is also what stops the redirect bouncing. */
export function tenantLessDeepLink(
    router: Router,
    to: TenantLocation,
    from: TenantLocation | undefined,
    fallbackTenant: string = DEFAULT_TENANT,
): RouteLocationRaw | undefined {
    if (!isTenantLessEntry(to)) {
        return undefined
    }

    const tenant = (from?.params?.tenant as string) || fallbackTenant
    const candidate = router.resolve({path: `/${tenant}${to.path}`, query: to.query, hash: to.hash})

    return isTenantLessEntry(candidate) ? undefined : candidate.fullPath
}

/** Fills in `:tenant` for every location given by name without one.
 *
 *  vue-router throws `Missing required param` rather than inheriting a required param from the current
 *  route, so without this every `router.push({name})` in the app would have to spell the tenant out.
 *  `push` and `replace` are patched alongside `resolve` because vue-router navigates through its own
 *  internal resolve closure, which patching `router.resolve` does not reach. */
export function installTenantParamInjection(router: Router, fallbackTenant: () => string = () => DEFAULT_TENANT): void {
    const declaresTenant = (name: unknown): boolean =>
        router.getRoutes().some((record) => record.name === name && TENANT_PARAM.test(record.path))

    const withTenant = (to: RouteLocationRaw): RouteLocationRaw => {
        if (!to || typeof to !== "object" || !("name" in to) || !to.name) {
            return to
        }

        const named = to as RouteLocationNamedRaw
        if (named.params?.tenant || !declaresTenant(named.name)) {
            return to
        }

        const tenant = (router.currentRoute.value?.params?.tenant as string) || fallbackTenant()
        return {...named, params: {...named.params, tenant}}
    }

    const parentResolve = router.resolve.bind(router)
    const parentPush = router.push.bind(router)
    const parentReplace = router.replace.bind(router)

    router.resolve = ((to: RouteLocationRaw, currentLocation?: RouteLocationNormalized) =>
        parentResolve(withTenant(to), currentLocation)) as Router["resolve"]
    router.push = ((to: RouteLocationRaw) => parentPush(withTenant(to))) as Router["push"]
    router.replace = ((to: RouteLocationRaw) => parentReplace(withTenant(to))) as Router["replace"]
}

export function setupTenantRouter(router: Router, app: App): void {
    installTenantParamInjection(router)

    app.config.globalProperties.$routeTo = function(to: RouteLocationRaw): RouteLocationRaw {
        if (typeof to === "string") {
            return to
        }

        const toWithParams = to as RouteLocationNamedRaw
        return {
            ...toWithParams,
            params: {tenant: this.$route?.params?.tenant || DEFAULT_TENANT, ...toWithParams.params},
        }
    }
}
