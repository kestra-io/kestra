export const PRELOAD_ERROR_RELOAD_KEY = "kestra:vite-preload-error-reload-at"

export function hasReloadedAfterPreloadError(storage: Storage = window.sessionStorage) {
    try {
        return storage.getItem(PRELOAD_ERROR_RELOAD_KEY) !== null
    } catch {
        return false
    }
}

export function markPreloadErrorReloaded(storage: Storage = window.sessionStorage) {
    try {
        storage.setItem(PRELOAD_ERROR_RELOAD_KEY, String(Date.now()))
        return true
    } catch {
        return false
    }
}
