import NProgress from "nprogress"
import type {Router} from "vue-router"
import {configureClient} from "@kestra-io/kestra-sdk"
import type {AxiosInstance, AxiosRequestConfig} from "axios"

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
    /** true for OSS (basic-auth) deployments, false for EE (OAuth/JWT) */
    oss?: boolean
    timeout?: number
    /** Timeout in ms for the token-refresh request */
    authTimeout?: number
    router?: Router
    coreStore?: {message: unknown; error: unknown}
    authStore?: {isLogged: boolean; logout(): Promise<void>}
    beforeLogout?: () => void
    isLoggedIn?: () => boolean
    isImpersonating?: () => boolean
    /** Called on a 401 when oss=true and the user is not logged in. Return false to suppress navigation. */
    onAuthTimeout?: () => boolean | void
    onError?: (type: "message" | "error", error: unknown) => void
}

const REFRESHED_HEADER = "X-JWT-Refreshed"

// ── Main setup function ──────────────────────────────────────────────────────

/**
 * Creates and configures the Kestra axios instance.
 *
 * Delegates generic setup (content-type, accept, paramsSerializer, querySerializer,
 * generated OpenAPI client wiring) to {@link configureClient} from the SDK, then
 * adds Kestra-specific interceptors: NProgress, auth/token-refresh, and error handling.
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
        oss = false,
        timeout = 0,
        authTimeout = 5_000,
        router,
        coreStore,
        authStore,
        beforeLogout,
        isImpersonating = () => false,
        isLoggedIn = () => authStore?.isLogged ?? false,
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

    // ── Response: auth + error handling ─────────────────────────────────────
    let toRefreshQueue: Array<{config: AxiosRequestConfig; resolve: (r: unknown) => void}> = []
    let refreshing = false

    function navigateToLogin() {
        if (!router) return
        const currentPath = window.location.pathname
        router.push({
            name: "login",
            query: currentPath.includes("/login") ? {} : {from: currentPath},
        })
    }

    instance.interceptors.response.use(undefined, async (errorResponse) => {
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

        // OSS: unauthenticated 401 → redirect to login
        if (status === 401 && oss && !isLoggedIn()) {
            if (options.onAuthTimeout?.() !== false) navigateToLogin()
            return Promise.reject(errorResponse)
        }

        // EE: authenticated 401 with no JWT cookie and not impersonating → try token refresh
        if (
            status === 401
            && isLoggedIn()
            && !oss
            && !document?.cookie.split("; ").map((c) => c.split("=")[0]).includes("JWT")
            && !isImpersonating()
        ) {
            const originalRequest: AxiosRequestConfig = errorResponse.config
            if (!originalRequest) return Promise.reject(errorResponse)

            if (originalRequest.url?.includes("/oauth/access_token")) {
                refreshing = false
                toRefreshQueue = []
                beforeLogout?.()
                delete instance.defaults.headers.common["Authorization"]
                authStore?.logout().catch(() => {})
                navigateToLogin()
                return Promise.reject(errorResponse)
            }

            if (!refreshing) {
                if ((originalRequest.headers as Record<string, string>)?.[REFRESHED_HEADER] === "1") {
                    return Promise.reject(errorResponse)
                }
                refreshing = true
                try {
                    await instance.post("/oauth/access_token?grant_type=refresh_token", null, {
                        headers: {"Content-Type": "application/json"},
                        timeout: authTimeout,
                    })
                    await Promise.allSettled(
                        toRefreshQueue.map(({config, resolve}) =>
                            instance.request(config).then(resolve).catch((e: unknown) => {
                                console.warn("Queued request failed after token refresh:", e)
                                throw e
                            })
                        )
                    )
                    toRefreshQueue = []
                    refreshing = false
                    ;(originalRequest.headers as Record<string, string>)[REFRESHED_HEADER] = "1"
                    return instance(originalRequest)
                } catch (refreshError) {
                    console.warn("Token refresh failed:", refreshError)
                    refreshing = false
                    toRefreshQueue = []
                    beforeLogout?.()
                    delete instance.defaults.headers.common["Authorization"]
                    authStore?.logout().catch(() => {})
                    navigateToLogin()
                    return Promise.reject(errorResponse)
                }
            } else {
                return new Promise((resolve, reject) => {
                    toRefreshQueue.push({config: originalRequest, resolve})
                    setTimeout(() => reject(new Error("Token refresh timeout")), 10_000)
                })
            }
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
