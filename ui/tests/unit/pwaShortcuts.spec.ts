import {describe, it, expect} from "vitest"
import {createRouter, createMemoryHistory} from "vue-router"

import routes from "../../src/routes/routes"
import {pwaShortcuts} from "../../plugins/pwaShortcuts"

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
    // installed jump list silently.
    it.each(pwaShortcuts)("resolves $url to its route", ({url}) => {
        expect(router.resolve(`/${url}`).name).toBe(EXPECTED_ROUTE_BY_URL[url])
    })
})
