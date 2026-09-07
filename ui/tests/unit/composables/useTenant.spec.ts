import {describe, it, expect, vi} from "vitest"
import {createApp} from "vue"
import {createRouter, createMemoryHistory} from "vue-router"

import {setupTenantRouter} from "../../../src/composables/useTenant"

function buildRouter() {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/", name: "home", component: {template: "<div/>"}},
            {path: "/:tenant?/logs", name: "logs/list", component: {template: "<div/>"}},
            {path: "/:tenant?/executions/:id", name: "executions/update", component: {template: "<div/>"}},
        ],
    })
    const app = createApp({template: "<router-view/>"})
    app.use(router)
    setupTenantRouter(router, app)
    return router
}

describe("setupTenantRouter", () => {
    it("still resolves the tenant-corrected URL for the very first navigation of a session", async () => {
        // vue-router's own first-ever navigation already behaves like a replace
        // regardless of what a guard returns, so this only pins the resolved
        // URL; the meaningful regression check is the second test below.
        const router = buildRouter()
        await router.isReady()

        await router.push({name: "logs/list"})

        expect(router.currentRoute.value.fullPath).toBe("/main/logs")
    })

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
