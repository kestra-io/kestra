import {beforeEach, describe, expect, it, vi} from "vitest"

const {loadAutocomplete, hasAnyActionOnAnyNamespace} = vi.hoisted(() => ({loadAutocomplete: vi.fn(), hasAnyActionOnAnyNamespace: vi.fn()}))

vi.mock("override/stores/namespaces", () => ({useNamespacesStore: () => ({loadAutocomplete})}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: {hasAnyActionOnAnyNamespace}})}))

import {namespaceValueProvider} from "./configurations/namespaceValueProvider"

describe("namespaceValueProvider", () => {
    beforeEach(() => {
        loadAutocomplete.mockReset().mockResolvedValue(["company.team.etl", "company.data", "other"])
        hasAnyActionOnAnyNamespace.mockReset().mockReturnValue(true)
    })

    /** Filtering on a parent namespace has to be possible even when only its children hold flows. */
    it("offers each namespace and every one of its parents, once", async () => {
        const values = await namespaceValueProvider()()

        expect(values.map(value => value.value)).toEqual(["company", "company.team", "company.team.etl", "company.data", "other"])
    })

    /** Each list asks for its own grant, and a user without it gets no suggestion and no request. */
    it("checks the grant the filter asks for, and offers nothing without it", async () => {
        hasAnyActionOnAnyNamespace.mockReturnValue(false)

        expect(await namespaceValueProvider("CASE", "VIEW")()).toEqual([])
        expect(hasAnyActionOnAnyNamespace).toHaveBeenCalledWith("CASE", "VIEW")
        expect(loadAutocomplete).not.toHaveBeenCalled()
    })
})
