export const PRELOAD_ERROR_RELOAD_KEY = "kestra:vite-preload-error-reloaded"
export const PRELOAD_ERROR_RELOAD_WINDOW_MS = 10_000

type PreloadErrorReloadHandlerOptions = {
    storage?: Storage
    reload?: () => void
    logger?: Pick<Console, "error">
}

export function hasReloadedAfterPreloadError(storage: Storage = window.sessionStorage, windowMs = PRELOAD_ERROR_RELOAD_WINDOW_MS) {
    try {
        const last = Number(storage.getItem(PRELOAD_ERROR_RELOAD_KEY) ?? 0)
        return last > 0 && Date.now() - last < windowMs
    } catch {
        return false
    }
}

export function setupPreloadErrorReloadHandler({
    storage = window.sessionStorage,
    reload = () => window.location.reload(),
    logger = console,
}: PreloadErrorReloadHandlerOptions = {}) {
    const handler = (event: WindowEventMap["vite:preloadError"]) => {
        if (hasReloadedAfterPreloadError(storage)) {
            logger.error("Stale lazy chunk detected, but a reload was already attempted recently", event.payload)
            return
        }

        if (markPreloadErrorReloaded(storage)) {
            event.preventDefault()
            reload()
        } else {
            logger.error("Stale lazy chunk detected, but the reload guard could not be persisted", event.payload)
        }
    }

    window.addEventListener("vite:preloadError", handler)

    return () => window.removeEventListener("vite:preloadError", handler)
}

export function markPreloadErrorReloaded(storage: Storage = window.sessionStorage) {
    try {
        storage.setItem(PRELOAD_ERROR_RELOAD_KEY, String(Date.now()))
        return true
    } catch {
        return false
    }
}
