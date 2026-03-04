import type {Router, RouteLocationNormalized, RouteLocationRaw, RouteLocationNamedRaw} from "vue-router";
import type {App} from "vue";

export function setupTenantRouter(router: Router, app: App): void {
    // Auto-inject tenant in route resolution with "main" as default
    const originalResolve = router.resolve;
    router.resolve = function(to: RouteLocationRaw, currentLocation?: RouteLocationNormalized) {
        if (to && typeof to === "object" && "name" in to && to.name && (!to.params || !to.params.tenant)) {
            to = {...to, params: {tenant: "main", ...to.params}};
        }
        return originalResolve.call(this, to, currentLocation);
    };

    app.config.globalProperties.$routeTo = function(to: RouteLocationRaw): RouteLocationRaw {
        if (typeof to === "string") {
            return to;
        }

        const toWithParams = to as RouteLocationNamedRaw;
        return {
            ...toWithParams,
            params: {tenant: this.$route?.params?.tenant || "main", ...toWithParams.params}
        };
    };

    router.beforeEach((to, from, next) => {
        // on login, prevent redirection to tenant
        if (to.meta?.anonymous === true) {
            return next();
        }
        if (to.path !== "/" && !to.params.tenant) {
            // Use current tenant from route context, fallback to "main"
            const currentTenant = from.params?.tenant || "main";
            next({path: `/${currentTenant}${to.path}`, query: to.query, hash: to.hash, replace: true});
        } else {
            next();
        }
    });
}