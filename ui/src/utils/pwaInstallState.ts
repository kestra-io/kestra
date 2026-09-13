import {ref} from "vue"

export interface BeforeInstallPromptEvent extends Event {
    readonly userChoice: Promise<{outcome: "accepted" | "dismissed", platform: string}>
    prompt(): Promise<void>
}

export const deferredInstallPrompt = ref<BeforeInstallPromptEvent | null>(null)
export const appInstalled = ref(false)

let initialized = false

// captured at bootstrap (main.ts) so the event isn't missed on login-first routes
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
