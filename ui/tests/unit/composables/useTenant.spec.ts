import {describe, it, expect, vi} from "vitest"
import {createApp} from "vue"
import {createRouter, createMemoryHistory} from "vue-router"

import {installTenantParamInjection, setupTenantRouter, tenantGuard, tenantLessDeepLink} from "../../../src/composables/useTenant"

// Mirrors main.ts, where the guard goes through initApp: registering it after one of initApp's
// awaits would leave the router's first navigation, started by app.use(router), without it.
function buildRouter() {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/", name: "home", component: {template: "<div/>"}},
            {path: "/:tenant/logs", name: "logs/list", component: {template: "<div/>"}},
            {path: "/:tenant/executions/:id", name: "executions/update", component: {template: "<div/>"}},
            {path: "/:tenant/assets/:assetId", name: "assets/update", component: {template: "<div/>"}},
            {path: "/login", name: "login", component: {template: "<div/>"}, meta: {anonymous: true}},
            {path: "/:tenant/:pathMatch(.*)", name: "errors/404-wildcard", component: {template: "<div/>"}},
        ],
    })
    router.beforeEach((to, from) => tenantGuard(router, to, from))
    const app = createApp({template: "<router-view/>"})
    app.use(router)
    setupTenantRouter(router, app)
    return router
}

describe("setupTenantRouter", () => {
    it("does not collapse a later in-app navigation that also lacks an explicit tenant", async () => {
        const router = buildRouter()
        await router.isReady()
        await router.push({name: "logs/list"})

        const replaceSpy = vi.spyOn(router.options.history, "replace")
        const pushSpy = vi.spyOn(router.options.history, "push")

        // A real second hop - e.g. a <router-link> built without $routeTo, so it
        // still lacks an explicit tenant param - must still land on a *new*
        // history entry, not overwrite the page the user was already on.
        await router.push({name: "executions/update", params: {id: "123"}})

        expect(router.currentRoute.value.fullPath).toBe("/main/executions/123")
        expect(pushSpy).toHaveBeenCalledTimes(1)
        expect(replaceSpy).not.toHaveBeenCalled()
    })
})

describe("installTenantParamInjection", () => {
    it("fills in the required tenant a named location leaves out, keeping the one already open", async () => {
        const router = buildRouter()
        await router.push("/acme/logs")

        // vue-router throws Missing required param rather than inheriting :tenant from the current
        // route, so without the injection every call site would have to spell the tenant out.
        await router.push({name: "executions/update", params: {id: "123"}})

        expect(router.currentRoute.value.fullPath).toBe("/acme/executions/123")
    })

    it("leaves a route that declares no tenant alone", async () => {
        const router = buildRouter()
        await router.push("/acme/logs")

        await router.push({name: "login"})

        expect(router.currentRoute.value.fullPath).toBe("/login")
    })

    it("does not override a tenant the caller asked for", () => {
        const router = buildRouter()

        expect(router.resolve({name: "logs/list", params: {tenant: "other"}}).fullPath).toBe("/other/logs")
    })

    it("falls back to the given tenant when nothing is open yet", () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/:tenant/logs", name: "logs/list", component: {template: "<div/>"}}],
        })
        installTenantParamInjection(router, () => "remembered")

        expect(router.resolve({name: "logs/list"}).fullPath).toBe("/remembered/logs")
    })
})

describe("tenantLessDeepLink", () => {
    const from = {params: {tenant: "acme"}} as never

    it("retries a tenant-less deep link under the current tenant", () => {
        const router = buildRouter()

        // `:tenant` is required, so `/assets/an-asset` reads `assets` as the tenant and matches nothing.
        const to = router.resolve("/assets/an-asset")
        expect(to.name).toBe("errors/404-wildcard")

        expect(tenantLessDeepLink(router, to, from)).toBe("/acme/assets/an-asset")
    })

    it("leaves a URL that already resolves alone", () => {
        const router = buildRouter()

        expect(tenantLessDeepLink(router, router.resolve("/acme/logs"), from)).toBeUndefined()
    })

    it("covers a single-segment URL, which matches no record at all", () => {
        const router = buildRouter()

        expect(router.resolve("/logs").matched).toHaveLength(0)
        expect(tenantLessDeepLink(router, router.resolve("/logs"), from)).toBe("/acme/logs")
    })

    it("gives up rather than bouncing when the retry would 404 as well", () => {
        const router = buildRouter()

        // Retrying `/acme/nope/nope` still lands on the wildcard, so returning it would send the
        // guard round again with a longer path every time.
        expect(tenantLessDeepLink(router, router.resolve("/nope/nope"), from)).toBeUndefined()
    })
})
