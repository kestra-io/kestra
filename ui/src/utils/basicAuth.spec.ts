import {afterAll, afterEach, describe, expect, it, vi} from "vitest"

import {isLoggedIn} from "./basicAuth"

function storage() {
    const values = new Map<string, string>()
    return {
        clear: () => values.clear(),
        getItem: (key: string) => values.get(key) ?? null,
        removeItem: (key: string) => values.delete(key),
        setItem: (key: string, value: string) => values.set(key, value),
    }
}

const localStorage = storage()
const sessionStorage = storage()

vi.stubGlobal("localStorage", localStorage)
vi.stubGlobal("sessionStorage", sessionStorage)

describe("basic auth", () => {
    afterEach(() => {
        localStorage.clear()
        sessionStorage.clear()
    })

    it("shouldRestoreAuthenticationInANewTab", () => {
        // Given
        localStorage.setItem("kestraBasicAuthenticated", "true")

        // When
        const loggedIn = isLoggedIn()

        // Then
        expect(loggedIn).toBe(true)
    })

    it("shouldNotTreatTabScopedAuthenticationAsLoggedIn", () => {
        // Given
        sessionStorage.setItem("kestraBasicAuthenticated", "true")

        // When
        const loggedIn = isLoggedIn()

        // Then
        expect(loggedIn).toBe(false)
    })
})

afterAll(() => {
    vi.unstubAllGlobals()
})
