import type {Router, RouteLocationNormalized, RouteLocationRaw, RouteLocationNamedRaw} from "vue-router"
import type {App} from "vue"

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

    router.beforeEach((to, from) => {
        // on login, prevent redirection to tenant
        if (to.meta?.anonymous === true) {
            return true
        }
        if (to.path !== "/" && !to.params.tenant) {
            // Use current tenant from route context, fallback to "main"
            const currentTenant = from.params?.tenant || "main"
            // Only collapse this into the current history entry when there is no
            // real previous page to preserve - i.e. the very first navigation of
            // the session (a bookmarked/typed URL missing the tenant segment).
            // Forcing `replace: true` unconditionally overwrites whatever page the
            // user was already on (e.g. the dashboard) with the tenant-corrected
            // target, silently dropping it from browser history: a later
            // navigation that also lacks an explicit tenant - any in-app link
            // built without $routeTo - then hits this same branch again, and the
            // "back" button skips straight over the missing entry.
            const isInitialNavigation = from.matched.length === 0
            return {path: `/${currentTenant}${to.path}`, query: to.query, hash: to.hash, replace: isInitialNavigation}
        }
        return true
    })
}