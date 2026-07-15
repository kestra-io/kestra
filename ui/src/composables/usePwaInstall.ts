import {computed, onBeforeUnmount, onMounted, ref} from "vue"

interface BeforeInstallPromptEvent extends Event {
    readonly userChoice: Promise<{outcome: "accepted" | "dismissed", platform: string}>
    prompt(): Promise<void>
}

function isStandalone(): boolean {
    return window.matchMedia("(display-mode: standalone)").matches ||
        // iOS Safari has no display-mode media query support; it exposes this instead.
        (navigator as Navigator & {standalone?: boolean}).standalone === true
}

function isSecureOrigin(): boolean {
    return window.location.protocol === "https:" || window.location.hostname === "localhost"
}

export function usePwaInstall() {
    const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null)
    const installed = ref(isStandalone())
    const dismissed = ref(false)

    const canInstall = computed(() =>
        deferredPrompt.value !== null && !installed.value && !dismissed.value && isSecureOrigin(),
    )

    function onBeforeInstallPrompt(event: Event) {
        event.preventDefault()
        deferredPrompt.value = event as BeforeInstallPromptEvent
    }

    function onAppInstalled() {
        installed.value = true
        deferredPrompt.value = null
    }

    onMounted(() => {
        window.addEventListener("beforeinstallprompt", onBeforeInstallPrompt)
        window.addEventListener("appinstalled", onAppInstalled)
    })

    onBeforeUnmount(() => {
        window.removeEventListener("beforeinstallprompt", onBeforeInstallPrompt)
        window.removeEventListener("appinstalled", onAppInstalled)
    })

    async function promptInstall(): Promise<void> {
        const prompt = deferredPrompt.value
        if (!prompt) {
            return
        }

        await prompt.prompt()
        await prompt.userChoice
        deferredPrompt.value = null
    }

    function dismiss(): void {
        dismissed.value = true
    }

    return {canInstall, promptInstall, dismiss, dismissed}
}
