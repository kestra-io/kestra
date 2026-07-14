import {describe, expect, test, vi} from "vitest"
import {
    hasReloadedAfterPreloadError,
    markPreloadErrorReloaded,
    PRELOAD_ERROR_RELOAD_KEY,
    setupPreloadErrorReloadHandler,
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

    test("stores a session reload marker", () => {
        const storage = createStorageMock()

        expect(markPreloadErrorReloaded(storage)).toBe(true)

        expect(storage.setItem).toHaveBeenCalledWith(PRELOAD_ERROR_RELOAD_KEY, "true")
        expect(hasReloadedAfterPreloadError(storage)).toBe(true)
    })

    test("reloads once when multiple preload errors are dispatched", () => {
        const storage = createStorageMock()
        const reload = vi.fn()
        const logger = {error: vi.fn()}
        const cleanup = setupPreloadErrorReloadHandler({storage, reload, logger})

        const firstEvent = new Event("vite:preloadError", {cancelable: true})
        const secondEvent = new Event("vite:preloadError", {cancelable: true})

        window.dispatchEvent(firstEvent)
        window.dispatchEvent(secondEvent)
        cleanup()

        expect(reload).toHaveBeenCalledOnce()
        expect(firstEvent.defaultPrevented).toBe(true)
        expect(secondEvent.defaultPrevented).toBe(false)
        expect(logger.error).toHaveBeenCalledOnce()
    })
})
