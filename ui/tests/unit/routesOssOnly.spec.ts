import {describe, it, expect} from "vitest"

import routes from "../../src/routes/routes"

describe("routes ossOnly marker", () => {
    it("flags the basic-auth setup wizard as ossOnly", () => {
        // Given
        const setup = routes.find(route => route.name === "setup")

        // Then
        // EE filters its route table on this flag. The wizard posts to /api/v1/{tenant}/basicAuth,
        // an OSS-only endpoint, so shipping it to EE strands users on a page that can only fail.
        expect(setup).toBeDefined()
        expect(setup?.ossOnly).toBe(true)
    })

    it("keeps every other route registrable by downstream editions", () => {
        // Given
        const ossOnlyNames = routes.filter(route => route.ossOnly).map(route => route.name)

        // Then
        // Guards against the flag spreading by copy-paste: adding one is a deliberate act that
        // removes the page from EE, so it should show up in a diff of this list.
        expect(ossOnlyNames).toEqual(["setup"])
    })
})
