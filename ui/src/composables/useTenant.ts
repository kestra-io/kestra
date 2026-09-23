import type {Router, RouteLocationNormalized, RouteLocationRaw, RouteLocationNamedRaw} from "vue-router"
import type {App} from "vue"

/** Rewrites a tenant-less path to the current tenant, defaulting to "main". Must be registered through
 *  `initApp`: a guard registered after one of its awaits is missed by the router's first navigation. */
export function tenantGuard(_router: Router, to: RouteLocationNormalized, from: RouteLocationNormalized): boolean | RouteLocationRaw {
    // on login, prevent redirection to tenant
    if (to.meta?.anonymous === true) {
        return true
    }
    if (to.path !== "/" && !to.params.tenant) {
        // Use current tenant from route context, fallback to "main"
        const currentTenant = from.params?.tenant || "main"
        // No `replace` here: vue-router forces one on the first navigation, keeping the tenant-less URL
        // out of history, and setting it explicitly would turn a caller's `router.replace()` into a `push`.
        return {path: `/${currentTenant}${to.path}`, query: to.query, hash: to.hash}
    }
    return true
}

export function setupTenantRouter(router: Router, app: App): void {
    // Auto-inject tenant in route resolution with "main" as default
    const originalResolve = router.resolve
    router.resolve = function(to: RouteLocationRaw, currentLocation?: RouteLocationNormalized) {
        if (to && typeof to === "object" && "name" in to && to.name && (!to.params || !to.params.tenant)) {
            to = {...to, params: {tenant: "main", ...to.params}}
        }
        return originalResolve.call(this, to, currentLocation)
    }

    app.config.globalProperties.$routeTo = function(to: RouteLocationRaw): RouteLocationRaw {
        if (typeof to === "string") {
            return to
        }

        const toWithParams = to as RouteLocationNamedRaw
        return {
            ...toWithParams,
            params: {tenant: this.$route?.params?.tenant || "main", ...toWithParams.params},
        }
    }
}
