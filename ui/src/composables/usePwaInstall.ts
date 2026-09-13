import {computed, ref} from "vue"
import {appInstalled, deferredInstallPrompt, initPwaInstallCapture} from "../utils/pwaInstallState"

function isStandalone(): boolean {
    return window.matchMedia("(display-mode: standalone)").matches ||
        // iOS Safari: no display-mode query, exposes navigator.standalone instead
        (navigator as Navigator & {standalone?: boolean}).standalone === true
}

function isSecureOrigin(): boolean {
    return window.location.protocol === "https:" || window.location.hostname === "localhost"
}

export function usePwaInstall() {
    initPwaInstallCapture()

    const installed = ref(isStandalone())
    const dismissed = ref(false)

    const canInstall = computed(() =>
        deferredInstallPrompt.value !== null && !installed.value && !appInstalled.value && !dismissed.value && isSecureOrigin(),
    )

    async function promptInstall(): Promise<void> {
        const prompt = deferredInstallPrompt.value
        if (!prompt) {
            return
        }

        await prompt.prompt()
        await prompt.userChoice
        deferredInstallPrompt.value = null
    }

    function dismiss(): void {
        dismissed.value = true
    }

    return {canInstall, promptInstall, dismiss, dismissed}
}
