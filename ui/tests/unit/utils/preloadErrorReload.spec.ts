import {afterEach, describe, expect, test, vi} from "vitest"
import {
    hasReloadedAfterPreloadError,
    markPreloadErrorReloaded,
    PRELOAD_ERROR_RELOAD_KEY,
    PRELOAD_ERROR_RELOAD_WINDOW_MS,
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

function createThrowingStorageMock() {
    return {
        getItem: vi.fn(() => null),
        setItem: vi.fn(() => {
            throw new DOMException("QuotaExceededError")
        }),
    } as unknown as Storage
}

describe("preloadErrorReload", () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    test("allows the first preload error reload in a session", () => {
        const storage = createStorageMock()

        expect(hasReloadedAfterPreloadError(storage)).toBe(false)
    })

    test("marks preload error reloads as handled within the guard window", () => {
        const storage = createStorageMock()

        expect(markPreloadErrorReloaded(storage)).toBe(true)

        expect(hasReloadedAfterPreloadError(storage)).toBe(true)
    })

    test("stores the reload timestamp", () => {
        const storage = createStorageMock()

        expect(markPreloadErrorReloaded(storage)).toBe(true)

        const [key, value] = (storage.setItem as ReturnType<typeof vi.fn>).mock.calls[0]
        expect(key).toBe(PRELOAD_ERROR_RELOAD_KEY)
        expect(Number(value)).toBeGreaterThan(0)
        expect(hasReloadedAfterPreloadError(storage)).toBe(true)
    })

    test("reloads once when multiple preload errors are dispatched within the window", () => {
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

    test("reloads again once the guard window has elapsed", () => {
        vi.useFakeTimers()
        const storage = createStorageMock()
        const reload = vi.fn()
        const cleanup = setupPreloadErrorReloadHandler({storage, reload})

        window.dispatchEvent(new Event("vite:preloadError", {cancelable: true}))
        vi.advanceTimersByTime(PRELOAD_ERROR_RELOAD_WINDOW_MS + 1)
        window.dispatchEvent(new Event("vite:preloadError", {cancelable: true}))
        cleanup()

        expect(reload).toHaveBeenCalledTimes(2)
    })

    test("falls back to the original error when the guard cannot be persisted", () => {
        const storage = createThrowingStorageMock()
        const reload = vi.fn()
        const logger = {error: vi.fn()}
        const cleanup = setupPreloadErrorReloadHandler({storage, reload, logger})

        const event = new Event("vite:preloadError", {cancelable: true})
        window.dispatchEvent(event)
        cleanup()

        expect(reload).not.toHaveBeenCalled()
        expect(event.defaultPrevented).toBe(false)
        expect(logger.error).toHaveBeenCalledOnce()
    })
})
