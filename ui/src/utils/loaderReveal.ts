// Shared between OSS and EE (imported by ui-ee via the "kestra/src/*" alias) so the
// loading-animation blink fix (delayed reveal + minimum visible time) lives in one place.
declare global {
    interface Window {
        __kestraLoader?: {
            shown: boolean
            shownAt: number
            showDelay: number
            minVisible: number
            timer: ReturnType<typeof setTimeout> | null
        }
    }
}

/**
 * Hides the boot loader and reveals the app container.
 *
 * If the loader was never shown (fast load, still within its show delay), it is hidden
 * instantly. If it was already shown, it stays visible for at least its configured minimum
 * duration and fades out, so the animation never just blinks on and off.
 *
 * @param onRevealed called once the app container has been made visible
 */
export function revealApp(onRevealed?: () => void) {
    const loader = document.getElementById("loader-wrapper")
    const appContainer = document.getElementById("app-container")
    const kestraLoader = window.__kestraLoader

    const reveal = () => {
        if (loader) {
            loader.classList.remove("is-visible")
            // let the fade-out transition finish before removing it from layout
            setTimeout(() => { loader.style.display = "none" }, 150)
        }
        if (appContainer) appContainer.style.display = "block"
        onRevealed?.()
    }

    if (kestraLoader?.timer) {
        clearTimeout(kestraLoader.timer)
    }

    if (kestraLoader?.shown) {
        const elapsed = performance.now() - kestraLoader.shownAt
        setTimeout(reveal, Math.max(0, kestraLoader.minVisible - elapsed))
    } else {
        reveal()
    }
}
