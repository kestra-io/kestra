export const PRELOAD_ERROR_RELOAD_KEY = "kestra:vite-preload-error-reloaded"

type PreloadErrorReloadHandlerOptions = {
    storage?: Storage
    reload?: () => void
    logger?: Pick<Console, "error">
}

export function hasReloadedAfterPreloadError(storage: Storage = window.sessionStorage) {
    try {
        return storage.getItem(PRELOAD_ERROR_RELOAD_KEY) !== null
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
            logger.error("Stale lazy chunk detected, but a reload was already attempted this session", event.payload)
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
        storage.setItem(PRELOAD_ERROR_RELOAD_KEY, "true")
        return true
    } catch {
        return false
    }
}
