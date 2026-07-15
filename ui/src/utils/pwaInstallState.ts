import {ref} from "vue"

export interface BeforeInstallPromptEvent extends Event {
    readonly userChoice: Promise<{outcome: "accepted" | "dismissed", platform: string}>
    prompt(): Promise<void>
}

export const deferredInstallPrompt = ref<BeforeInstallPromptEvent | null>(null)
export const appInstalled = ref(false)

let initialized = false

// Captured at bootstrap (see main.ts) so the event is never missed when the
// user first lands on an anonymous route (e.g. /login), where PwaInstallPrompt
// is not yet mounted. A single listener lives for the app's lifetime; it must
// not be re-registered per composable/component instance.
export function initPwaInstallCapture(): void {
    if (initialized || typeof window === "undefined") {
        return
    }
    initialized = true

    window.addEventListener("beforeinstallprompt", (event: Event) => {
        event.preventDefault()
        deferredInstallPrompt.value = event as BeforeInstallPromptEvent
    })

    window.addEventListener("appinstalled", () => {
        appInstalled.value = true
        deferredInstallPrompt.value = null
    })
}
