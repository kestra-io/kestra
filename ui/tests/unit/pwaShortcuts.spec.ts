import {describe, it, expect} from "vitest"
import {createRouter, createMemoryHistory} from "vue-router"

import routes from "../../src/routes/routes"
import {pwaShortcuts} from "../../plugins/pwaShortcuts"

describe("pwa manifest shortcuts", () => {
    const router = createRouter({history: createMemoryHistory(), routes})

    // Nothing in the app navigates through the manifest, so a renamed route would break the
    // installed jump list silently.
    it.each(pwaShortcuts)("resolves $url to a real route", ({url}) => {
        expect(router.resolve(`/${url}`).name).not.toBe("errors/404-wildcard")
    })
})
