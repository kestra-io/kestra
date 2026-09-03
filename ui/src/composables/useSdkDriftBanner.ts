import {onMounted, onUnmounted, ref} from "vue"

// Mirrors SDK_DRIFT_EVENT in @kestra-io/kestra-sdk's dev-freshness.ts — kept as a plain string here
// rather than an import since that module is intentionally internal (dynamically imported by the
// SDK package itself, not part of its public exports map).
const SDK_DRIFT_EVENT = "kestra:sdk-drift"

export interface SdkDriftEventDetail {
    label: string
    committedHash: string
    liveHash: string
}

export function useSdkDriftBanner() {
    const detail = ref<SdkDriftEventDetail | null>(null)
    const dismissed = ref(false)

    function onDrift(event: Event) {
        detail.value = (event as CustomEvent<SdkDriftEventDetail>).detail
    }

    onMounted(() => window.addEventListener(SDK_DRIFT_EVENT, onDrift))
    onUnmounted(() => window.removeEventListener(SDK_DRIFT_EVENT, onDrift))

    function dismiss(): void {
        dismissed.value = true
    }

    return {detail, dismissed, dismiss}
}
