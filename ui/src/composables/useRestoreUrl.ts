/**
 * Persists a page's URL query (filters, sort, page size, etc.) to sessionStorage so it
 * can be restored when the user navigates back to that page with no query. The page
 * number is excluded (PY-1529) from restoration so returning lands on the first page.
 *
 * Auto-saves on every same-path query change and auto-restores on mount when the
 * URL has no query but a saved state exists. Exposes `loadInit` so consumers can
 * gate their initial data fetch and avoid racing the in-flight restore navigation.
 */
import {computed, onMounted, ref, watch} from "vue"
import {isNavigationFailure, NavigationFailureType, RouteLocation, Router, useRoute, useRouter} from "vue-router"

interface UseRestoreUrlOptions {
    restoreUrl?: boolean;
}

function getLocalStorageName(route: RouteLocation): string {
    // Only entity-identifying params present from first render: the dashboard route
    // appends its `dashboard` param after mount, and including it here would change
    // the key mid-restore, breaking the re-assert loop in goToRestoreUrl().
    const scope = (["tab", "namespace", "id", "kind", "tenant"] as const)
        .map((key) => route.params[key])
        .filter((value) => value)
        .map((value) => "_" + value)
        .join("")

    return `${route.name?.toString().replace("/", "_")}${scope}_restore_url`
}

function getRestoredUrlValue(route: RouteLocation) {
    const raw = window.sessionStorage.getItem(getLocalStorageName(route))
    if (!raw) return null

    try {
        return JSON.parse(raw)
    } catch {
        // Prevent crashes from malformed sessionStorage data
        return null
    }
}

export function getRestoredQuery(route: RouteLocation) {
    const localStorageValue = getRestoredUrlValue(route)
    if (localStorageValue === null) {
        return {query: route.query, change: false, localStorageValue}
    }

    const query = {...route.query}
    let change = false

    for (const key in localStorageValue) {
        const value = localStorageValue[key]
        if (key === "page" || query[key] || !value) continue
        // empty array breaks the application
        if (Array.isArray(value) && value.length === 0) continue
        query[key] = value
        change = true
    }

    return {query, change, localStorageValue}
}

/**
 * Resolves once the navigation that cancelled ours has finished, so the retry starts
 * from the settled URL instead of cancelling that navigation right back. The timeout
 * covers a navigation that already settled before we subscribed.
 */
function navigationSettled(router: Router): Promise<void> {
    return new Promise((resolve) => {
        const stop = router.afterEach(() => {
            stop()
            resolve()
        })
        setTimeout(() => {
            stop()
            resolve()
        }, 100)
    })
}

export default function useRestoreUrl(options: UseRestoreUrlOptions = {}) {
    const {restoreUrl = true} = options

    const route = useRoute()
    const router = useRouter()

    const loadInit = ref(true)

    const localStorageName = computed(() => getLocalStorageName(route))

    const localStorageValue = computed(() => {
        const raw = window.sessionStorage.getItem(localStorageName.value)
        return raw ? JSON.parse(raw) : null
    })

    let restoring = false

    const saveRestoreUrl = () => {
        // The navigation that cancels our restore reaches this watcher first, so saving
        // here would overwrite the very state the retry below is about to re-assert.
        if (restoring) return
        if (!restoreUrl || route.query.noRestore) return
        if (Object.keys(route.query).length === 0) {
            window.sessionStorage.removeItem(localStorageName.value)
        } else {
            window.sessionStorage.setItem(localStorageName.value, JSON.stringify(route.query))
        }
    }

    const goToRestoreUrl = async () => {
        // Unblock loadData synchronously — the consumer's route.query watcher
        // fires before router.replace's .then, and that reload must see loadInit=true.
        loadInit.value = true

        // A page that rewrites its own URL on mount (e.g. the dashboard appending its
        // id param) cancels our replace and the restored filters are lost, so re-assert
        // them once that navigation has settled.
        restoring = true
        try {
            for (let attempt = 0; attempt < 2; attempt++) {
                const {query, change} = getRestoredQuery(route)
                if (!change) return

                const failure = await router.replace({query})
                if (!isNavigationFailure(failure, NavigationFailureType.cancelled)) return

                await navigationSettled(router)
            }
        } finally {
            restoring = false
        }
    }

    // Settle loadInit in setup so children can gate their first load and avoid
    // racing the in-flight restore navigation.
    if (restoreUrl && localStorageValue.value && Object.keys(route.query).length === 0) {
        const {change} = getRestoredQuery(route)
        if (change) {
            loadInit.value = false
        }
    }

    onMounted(() => {
        if (!loadInit.value) goToRestoreUrl()
    })

    // Skip cross-route navigations so leaving a page doesn't clobber another
    // route's saved state with the new route's empty query.
    watch(() => route.fullPath, (newPath, oldPath) => {
        if (oldPath && newPath.split("?")[0] !== oldPath.split("?")[0]) return
        saveRestoreUrl()
    })

    return {
        loadInit,
        localStorageName,
        localStorageValue,
        saveRestoreUrl,
        goToRestoreUrl,
    }
}
