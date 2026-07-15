export const SERVICE_WORKER_FILENAME = "sw.js"

// A service worker's max allowed scope is capped by the directory it is served from
// (unless the server sends a `Service-Worker-Allowed` header, which we don't set - no
// webserver change). sw.js is emitted at the root of the Vite build output, which the
// webserver maps to "<basePath>/ui/**" (see cli/src/main/resources/application.yml,
// micronaut.router.static-resources.ui). The scope must therefore be "<basePath>/ui/",
// not the bare deploy basePath - registering with the basePath alone would target a
// directory *above* where sw.js is served and throw a SecurityError.
export function computeSwScope(basePath: string | undefined | null): string {
    const withLeadingSlash = (basePath ?? "").startsWith("/") ? (basePath ?? "") : `/${basePath ?? ""}`
    const withoutTrailingSlash = withLeadingSlash.replace(/\/+$/, "")
    return `${withoutTrailingSlash}/ui/`
}

export async function registerServiceWorker(): Promise<void> {
    // sw.js is only emitted by a production build (see vite.config.js VitePWA); the vite
    // dev server never serves it, so registering here would just 404 on every dev reload.
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
