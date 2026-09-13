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
            // No `replace` here: vue-router merges whatever this guard returns
            // over the outer navigation's own options, so setting it explicitly
            // - even to `false` - would override a caller's `router.replace()`
            // and silently turn it into a `push`. Leaving it out lets
            // vue-router's own first-navigation handling (which already forces
            // a replace when there is no real previous page to preserve) do the
            // right thing, while every later navigation keeps whatever
            // push/replace semantics its caller intended.
            return {path: `/${currentTenant}${to.path}`, query: to.query, hash: to.hash}
        }
        return true
    })
}