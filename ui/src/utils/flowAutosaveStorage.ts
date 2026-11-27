export const AUTOSAVE_STORAGE_PREFIX = "kestra.flowAutosave."

/** Drops every flow draft this browser still holds, synced or not. */
export function clearAutosaveBuffers() {
    try {
        Object.keys(localStorage)
            .filter(key => key.startsWith(AUTOSAVE_STORAGE_PREFIX))
            .forEach(key => localStorage.removeItem(key))
    } catch {
        // Nothing to clean up when storage is blocked.
    }
}
