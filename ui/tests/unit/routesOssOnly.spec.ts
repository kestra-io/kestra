import {describe, it, expect} from "vitest"

import routes from "../../src/routes/routes"

describe("routes ossOnly marker", () => {
    it("flags the basic-auth setup wizard as ossOnly", () => {
        // Given
        const setup = routes.find(route => route.name === "setup")

        // Then
        // EE filters on this flag; without it the wizard ships to an edition that cannot serve it.
        expect(setup).toBeDefined()
        expect(setup?.ossOnly).toBe(true)
    })

    // kestra-io/kestra-ee#11139: an optional `:tenant` lets a longer tenant-less route outscore a
    // tenant-ful one, so `/ui/assets/flows` opens an asset called `flows` rather than the Flows page
    // of a tenant called `assets`. EE consumes this table, and EE is where that bites.
    it("declares the tenant as a required param on every tenant-scoped route", () => {
        const optional = routes.filter(route => String(route.path).includes(":tenant?")).map(route => route.name)

        expect(optional).toEqual([])
    })

    it("keeps every other route registrable by downstream editions", () => {
        // Given
        const ossOnlyNames = routes.filter(route => route.ossOnly).map(route => route.name)

        // Then
        // Flagging a route removes it from EE, so any addition should surface in this diff.
        expect(ossOnlyNames).toEqual(["setup"])
    })
})
