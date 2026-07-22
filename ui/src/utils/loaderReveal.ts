// Shared between OSS and EE (imported by ui-ee via the "kestra/src/*" alias) so the
// loading-animation blink fix (delayed reveal) lives in one place.
declare global {
    interface Window {
        __kestraLoader?: {
            showDelay: number
            timer: ReturnType<typeof setTimeout> | null
        }
    }
}

/**
 * Hides the boot loader and reveals the app container, as soon as the app is ready.
 *
 * The loader only ever became visible if loading took longer than its configured show delay
 * (see the inline bootstrap script in index.html) — that alone prevents the blink on fast
 * loads. There is no minimum visible time here: once the app is ready, it's shown immediately.
 *
 * @param onRevealed called once the app container has been made visible
 */
export function revealApp(onRevealed?: () => void) {
    const loader = document.getElementById("loader-wrapper")
    const appContainer = document.getElementById("app-container")
    const kestraLoader = window.__kestraLoader

    if (kestraLoader?.timer) {
        clearTimeout(kestraLoader.timer)
    }

    if (loader) {
        loader.classList.remove("is-visible")
        loader.style.display = "none"
    }
    if (appContainer) appContainer.style.display = "block"
    onRevealed?.()
}
