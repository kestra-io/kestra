export const SERVICE_WORKER_FILENAME = "sw.js"

// scope must match sw.js's served dir (<basePath>/ui/) or registration throws SecurityError
export function computeSwScope(basePath: string | undefined | null): string {
    const withLeadingSlash = (basePath ?? "").startsWith("/") ? (basePath ?? "") : `/${basePath ?? ""}`
    const withoutTrailingSlash = withLeadingSlash.replace(/\/+$/, "")
    return `${withoutTrailingSlash}/ui/`
}

export async function registerServiceWorker(): Promise<void> {
    if (!import.meta.env.PROD || !("serviceWorker" in navigator)) {
        return
    }

    const scope = computeSwScope(window.KESTRA_BASE_PATH)

    try {
        await navigator.serviceWorker.register(`${scope}${SERVICE_WORKER_FILENAME}`, {scope})
    } catch (error) {
        console.error("Service worker registration failed", error)
    }
}
