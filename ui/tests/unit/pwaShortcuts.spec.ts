import {describe, it, expect} from "vitest"
import {createRouter, createMemoryHistory, START_LOCATION} from "vue-router"

import routes from "../../src/routes/routes"
import {pwaShortcuts} from "../../plugins/pwaShortcuts"
import {tenantGuard} from "../../src/composables/useTenant"

const EXPECTED_ROUTE_BY_URL: Record<string, string> = {
    "flows/new": "flows/create",
    "flows": "flows/list",
    "executions": "executions/list",
    "ai": "ai",
}

describe("pwa manifest shortcuts", () => {
    const router = createRouter({history: createMemoryHistory(), routes})

    it("pins a destination for every shortcut", () => {
        expect(pwaShortcuts.map(({url}) => url).sort()).toEqual(Object.keys(EXPECTED_ROUTE_BY_URL).sort())
    })

    // Nothing in the app navigates through the manifest, so a renamed path would break the
    // installed jump list silently. The manifest is one static build artifact and so cannot carry a
    // tenant, which `:tenant` being required makes load-bearing: these URLs reach their page only
    // because `tenantGuard` prefixes the current tenant.
    it.each(pwaShortcuts)("resolves $url to its route through the tenant guard", ({url}) => {
        const to = router.resolve(`/${url}`)
        const redirect = tenantGuard(router, to, {...START_LOCATION, params: {tenant: "acme"}} as never)

        expect(redirect).not.toBe(true)
        expect(router.resolve(redirect as never).name).toBe(EXPECTED_ROUTE_BY_URL[url])
    })
})
