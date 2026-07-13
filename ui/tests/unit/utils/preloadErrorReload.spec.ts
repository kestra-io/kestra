import {describe, expect, test, vi} from "vitest"
import {
    hasReloadedAfterPreloadError,
    markPreloadErrorReloaded,
    PRELOAD_ERROR_RELOAD_KEY,
} from "../../../src/utils/preloadErrorReload"

function createStorageMock() {
    const data = new Map<string, string>()

    return {
        getItem: vi.fn((key: string) => data.get(key) ?? null),
        setItem: vi.fn((key: string, value: string) => {
            data.set(key, value)
        }),
    } as unknown as Storage
}

describe("preloadErrorReload", () => {
    test("allows the first preload error reload in a session", () => {
        const storage = createStorageMock()

        expect(hasReloadedAfterPreloadError(storage)).toBe(false)
    })

    test("marks preload error reloads as already handled for the session", () => {
        const storage = createStorageMock()

        expect(markPreloadErrorReloaded(storage)).toBe(true)

        expect(hasReloadedAfterPreloadError(storage)).toBe(true)
    })

    test("treats any stored value as already reloaded", () => {
        const storage = createStorageMock()

        storage.setItem(PRELOAD_ERROR_RELOAD_KEY, "0")

        expect(hasReloadedAfterPreloadError(storage)).toBe(true)
    })
})
