import NProgress from "nprogress"
import type {Router} from "vue-router"
import {configureClient} from "@kestra-io/kestra-sdk"
import type {AxiosInstance} from "axios"

// ── NProgress helpers ────────────────────────────────────────────────────────

let pendingRoute = false
let requestsTotal = 0
let requestsCompleted = 0

function progressComplete() {
    pendingRoute = false
    requestsTotal = 0
    requestsCompleted = 0
    NProgress.done()
}

function initProgress() {
    requestsTotal++
    if (requestsTotal === 1) {
        setTimeout(() => {
            NProgress.start()
            NProgress.set(requestsCompleted / requestsTotal)
        }, 0)
    } else {
        NProgress.set(requestsCompleted / requestsTotal)
    }
}

function increaseProgress() {
    setTimeout(() => {
        requestsCompleted++
        if (requestsCompleted >= requestsTotal) progressComplete()
        else NProgress.set(requestsCompleted / requestsTotal - 0.1)
    }, 50)
}

function isEmptyBody(data: unknown): boolean {
    if (data == null) return true
    if (data instanceof FormData) return [...data.keys()].length === 0
    if (typeof data === "object") return Object.keys(data as object).length === 0
    return false
}

// ── Types ────────────────────────────────────────────────────────────────────

export interface KestraAxiosOptions {
    timeout?: number
    router?: Router
    coreStore?: {message: unknown; error: unknown}
    beforeLogout?: () => void
    isLoggedIn?: () => boolean
    onError?: (type: "message" | "error", error: unknown) => void
    /**
     * Called on a 401 when the user is not logged in.
     * Defaults to navigating to the login route.
     * EE overrides this to implement token-refresh before falling back to login.
     * May return a Promise (e.g. a retried request after refresh); the interceptor
     * forwards that Promise so the original caller gets the refreshed response.
     */
    onUnauthorized?: (navigateToLogin: () => void, errorResponse: unknown) => Promise<unknown> | void
}

// ── Main setup function ──────────────────────────────────────────────────────

/**
 * Creates and configures the Kestra OSS axios instance.
 *
 * Delegates generic setup (content-type, accept, paramsSerializer, querySerializer,
 * generated OpenAPI client wiring) to {@link configureClient} from the SDK, then
 * adds Kestra-specific interceptors: NProgress, error handling, and 401 handling.
 *
 * EE extends this by passing a custom {@link KestraAxiosOptions.onUnauthorized} handler
 * that implements token-refresh before falling back to the default login redirect.
 *
 * @param clientConfig - Forwarded to {@link configureClient} (e.g. base URL overrides).
 * @param options - Kestra-specific configuration (stores, router, auth callbacks).
 * @returns The configured {@link AxiosInstance}.
 */
export function setupKestraAxios(
    clientConfig: Record<string, unknown> = {},
    options: KestraAxiosOptions = {},
): AxiosInstance {
    const {
        timeout = 0,
        router,
        coreStore,
        beforeLogout,
        isLoggedIn = () => false,
        onError = (type: "message" | "error", error: unknown) => {
            if (!coreStore) return
            const axiosError = error as {response?: {status: number; data: unknown}}
            if (type === "message") {
                coreStore.message = {
                    variant: "error",
                    response: axiosError.response,
                    content: axiosError.response?.data,
                }
            } else {
                coreStore.error = axiosError.response?.status
            }
        },
    } = options

    const progressInterceptor = (e: {loaded?: number; total?: number}) => {
        if (e?.loaded && e?.total) NProgress.inc(Math.floor(e.loaded) / e.total)
    }

    // configureClient creates the axios instance, configures content-type/accept/
    // paramsSerializer/querySerializer, and wires the generated OpenAPI client.
    // After the corresponding SDK change it also registers the instance for useClient().
    const instance = configureClient(clientConfig, {
        timeout,
        headers: {"Content-Type": "application/json"},
        withCredentials: true,
        onDownloadProgress: progressInterceptor,
        onUploadProgress: progressInterceptor,
    })

    function navigateToLogin() {
        if (!router) return
        const currentPath = window.location.pathname
        router.push({
            name: "login",
            query: currentPath.includes("/login") ? {} : {from: currentPath},
        })
    }

    const onUnauthorized = options.onUnauthorized ?? ((navigate: () => void) => {
        beforeLogout?.()
        navigate()
    })

    // ── Request: NProgress start + multipart empty-body fix ──────────────────
    instance.interceptors.request.use((config) => {
        if (typeof document !== "undefined") initProgress()
        if (
            String(config.headers?.["Content-Type"]).startsWith("multipart/form-data")
            && isEmptyBody(config.data)
        ) {
            config.data = new FormData()
        }
        return config
    })

    // ── Response: NProgress tick ─────────────────────────────────────────────
    instance.interceptors.response.use(
        (response) => {increaseProgress(); return response},
        (error) => {increaseProgress(); return Promise.reject(error)},
    )

    // ── Response: error handling ─────────────────────────────────────────────
    instance.interceptors.response.use(undefined, (errorResponse) => {
        if (errorResponse?.code === "ERR_BAD_RESPONSE" && !errorResponse?.response?.data) {
            onError("message", errorResponse)
            return Promise.reject(errorResponse)
        }
        if (!errorResponse.response) return Promise.reject(errorResponse)

        const {status} = errorResponse.response

        if (status === 404) {
            onError("error", errorResponse)
            return Promise.reject(errorResponse)
        }

        if (status === 401 && !isLoggedIn()) {
            const result = onUnauthorized(navigateToLogin, errorResponse)
            return result instanceof Promise ? result : Promise.reject(errorResponse)
        }

        if (status === 400) return Promise.reject(errorResponse.response.data)

        if (errorResponse.response.data && errorResponse?.config?.showMessageOnError !== false) {
            onError("message", errorResponse)
        }
        return Promise.reject(errorResponse)
    })

    // ── Router hooks: NProgress on navigation ────────────────────────────────
    router?.beforeEach(() => {
        if (pendingRoute) requestsTotal--
        pendingRoute = true
        initProgress()
    })
    router?.afterEach(() => {
        if (pendingRoute) {
            increaseProgress()
            pendingRoute = false
        }
    })

    return instance
}
