import {computed, ref} from "vue"
import {useStorage} from "@vueuse/core"
import {appInstalled, deferredInstallPrompt, initPwaInstallCapture} from "../utils/pwaInstallState"
import {storageKeys} from "../utils/constants"

export const PWA_INSTALL_SNOOZE_MS = 30 * 24 * 60 * 60 * 1000

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
    const dismissedAt = useStorage(storageKeys.PWA_INSTALL_DISMISSED_AT, 0)
    const dismissed = computed(() => Date.now() - dismissedAt.value < PWA_INSTALL_SNOOZE_MS)

    const canInstall = computed(() =>
        deferredInstallPrompt.value !== null && !installed.value && !appInstalled.value && !dismissed.value && isSecureOrigin(),
    )

    async function promptInstall(): Promise<void> {
        const prompt = deferredInstallPrompt.value
        if (!prompt) {
            return
        }

        await prompt.prompt()
        const {outcome} = await prompt.userChoice
        deferredInstallPrompt.value = null
        if (outcome === "dismissed") {
            dismiss()
        }
    }

    function dismiss(): void {
        dismissedAt.value = Date.now()
    }

    return {canInstall, promptInstall, dismiss, dismissed}
}
