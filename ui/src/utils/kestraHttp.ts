import NProgress from "nprogress"
import type {Router} from "vue-router"
import {configureClient} from "@kestra-io/kestra-sdk"

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

// ── Types ────────────────────────────────────────────────────────────────────

export interface KestraHttpRequestMeta {
    method: string
    url: string
    headers: Headers
}

export interface KestraHttpConfig {
    params?: Record<string, unknown>
    headers?: Record<string, string>
    /** Defaults to "json": parses as JSON when the response Content-Type says so, else text. */
    responseType?: "json" | "text" | "blob"
    /** Aborts the request after this many ms. Unlike axios, there is no default timeout. */
    timeout?: number
    validateStatus?: (status: number) => boolean
    showMessageOnError?: boolean
}

export interface KestraHttpResponse<T = any> {
    data: T
    status: number
    statusText: string
    headers: Record<string, string>
    // response.url after following redirects - the fetch equivalent of axios's
    // request.responseURL, used by a couple of call sites to trigger a direct
    // browser download of a signed redirect URL instead of streaming a blob.
    request: {responseURL: string}
    config: KestraHttpConfig & {method: string; url: string}
}

export interface KestraHttpError extends Error {
    status?: number
    response?: KestraHttpResponse
    config?: KestraHttpConfig & {method: string; url: string}
}

export interface KestraHttpClient {
    defaults: {headers: {common: Record<string, string>}}
    interceptors: {
        request: {
            /** Runs for every request made through this client, generated-endpoint calls included. */
            use(fn: (meta: KestraHttpRequestMeta) => void): void
        }
    }
    get<T = any>(url: string, config?: KestraHttpConfig): Promise<KestraHttpResponse<T>>
    post<T = any>(url: string, data?: unknown, config?: KestraHttpConfig): Promise<KestraHttpResponse<T>>
    put<T = any>(url: string, data?: unknown, config?: KestraHttpConfig): Promise<KestraHttpResponse<T>>
    patch<T = any>(url: string, data?: unknown, config?: KestraHttpConfig): Promise<KestraHttpResponse<T>>
    delete<T = any>(url: string, config?: KestraHttpConfig): Promise<KestraHttpResponse<T>>
}

export interface KestraHttpOptions {
    router?: Router
    coreStore?: {message: unknown; error: unknown}
    beforeLogout?: () => void
    isLoggedIn?: () => boolean
    onError?: (type: "message" | "error", error: unknown) => void
    /**
     * Called on a 401 when the user is not logged in. Return (or resolve) `true` to
     * retry the original request once more - e.g. EE attempts a silent token refresh
     * first and retries only if it succeeds. Defaults to navigating to the login route
     * without retrying.
     */
    onUnauthorized?: (navigateToLogin: () => void, error: KestraHttpError) => Promise<boolean> | boolean | void
}

// ── Query string / body helpers ───────────────────────────────────────────────

function withQuery(url: string, params?: Record<string, unknown>): string {
    if (!params) return url
    const search = new URLSearchParams()
    for (const [key, value] of Object.entries(params)) {
        if (value === undefined || value === null) continue
        if (Array.isArray(value)) {
            value.forEach(v => search.append(key, String(v)))
        } else {
            search.append(key, typeof value === "object" ? JSON.stringify(value) : String(value))
        }
    }
    const query = search.toString()
    if (!query) return url
    return url.includes("?") ? `${url}&${query}` : `${url}?${query}`
}

// ── Main setup function ──────────────────────────────────────────────────────

/**
 * Creates and configures the Kestra OSS HTTP client.
 *
 * Two request paths share this configuration:
 * - generated SDK endpoint calls (`ExecutionsAPI.foo()`, ...), which funnel through the
 *   fetch-based `client` singleton {@link configureClient} sets up;
 * - the ad-hoc {@link KestraHttpClient} returned here, used for the handful of calls with
 *   no matching generated endpoint (multipart uploads, blob/CSV downloads, `params`-based
 *   query strings, the community blueprints API, ...).
 *
 * Both paths get NProgress tracking, centralized error handling (401/404/400), and any
 * extra per-request header injection registered via `.interceptors.request.use()` (e.g.
 * EE's CSRF token).
 *
 * EE extends this by passing a custom {@link KestraHttpOptions.onUnauthorized} handler
 * that implements token-refresh before falling back to the default login redirect.
 *
 * @param clientConfig - Forwarded to {@link configureClient} (e.g. base URL overrides).
 * @param options - Kestra-specific configuration (stores, router, auth callbacks).
 * @returns The configured {@link KestraHttpClient}.
 */
export function setupKestraHttp(
    clientConfig: Record<string, unknown> = {},
    options: KestraHttpOptions = {},
): KestraHttpClient {
    currentHttpClient = buildKestraHttp(clientConfig, options)
    return currentHttpClient
}

let currentHttpClient: KestraHttpClient | undefined

/**
 * Get the client {@link setupKestraHttp} configured at app bootstrap. Mirrors the SDK's
 * own `useClient()` accessor pattern, for the ad-hoc calls that have no generated endpoint.
 */
export function useKestraHttp(): KestraHttpClient {
    if (!currentHttpClient) throw new Error("KestraHttpClient not initialized. Please call setupKestraHttp first.")
    return currentHttpClient
}

function buildKestraHttp(
    clientConfig: Record<string, unknown>,
    options: KestraHttpOptions,
): KestraHttpClient {
    const {
        router,
        coreStore,
        beforeLogout,
        isLoggedIn = () => false,
        onError = (type: "message" | "error", error: unknown) => {
            if (!coreStore) return
            const kestraError = error as KestraHttpError
            if (type === "message") {
                coreStore.message = {
                    variant: "error",
                    response: kestraError.response,
                    content: kestraError.response?.data,
                }
            } else {
                coreStore.error = kestraError.response?.status
            }
        },
        onUnauthorized = (navigate: () => void) => {
            beforeLogout?.()
            navigate()
            return false
        },
    } = options

    const requestInterceptors: Array<(meta: KestraHttpRequestMeta) => void> = []
    const defaults = {headers: {common: {} as Record<string, string>}}

    function navigateToLogin() {
        if (!router) return
        const currentPath = window.location.pathname
        router.push({
            name: "login",
            query: currentPath.includes("/login") ? {} : {from: currentPath},
        })
    }

    // ── Central error handling, shared by both request paths ────────────────
    // Runs AFTER `error.status`/`.message` have already been normalized (by the
    // SDK's own error interceptor for generated calls, or inline below for ad-hoc
    // calls), and BEFORE the 401-retry wrapper decides whether to retry.
    function handleErrorCentrally(error: KestraHttpError): KestraHttpError {
        const status = error.status
        if (status === 404) {
            onError("error", error)
        } else if (status !== 401 && status !== 400 && error.response?.data && error.config?.showMessageOnError !== false) {
            onError("message", error)
        }
        return error
    }

    // Wraps get/post/put/patch/delete (and the SDK's own client.request) so a 401
    // triggers `onUnauthorized` and, if it resolves truthy, retries the ORIGINAL call
    // exactly once (by re-invoking the unwrapped function with the same arguments) -
    // no config serialization/replay needed, and no risk of retry loops since the
    // retry call bypasses this wrapper.
    function withAuthRetry<F extends (...args: any[]) => Promise<any>>(fn: F): F {
        return (async (...args: Parameters<F>) => {
            try {
                return await fn(...args)
            } catch (error) {
                const kestraError = error as KestraHttpError
                if (kestraError.status === 401 && !isLoggedIn()) {
                    const shouldRetry = await onUnauthorized(navigateToLogin, kestraError)
                    if (shouldRetry) return fn(...args)
                }
                throw error
            }
        }) as F
    }

    // ── Ad-hoc client (no matching generated endpoint) ───────────────────────
    async function rawRequest<T>(method: string, url: string, data: unknown, config: KestraHttpConfig = {}): Promise<KestraHttpResponse<T>> {
        const fullUrl = withQuery(url, config.params)
        const headers = new Headers({...defaults.headers.common, ...config.headers})

        let body: BodyInit | undefined
        if (data instanceof FormData || data instanceof Blob) {
            body = data
        } else if (data !== undefined && data !== null) {
            body = JSON.stringify(data)
            if (!headers.has("Content-Type")) headers.set("Content-Type", "application/json")
        }

        requestInterceptors.forEach(fn => fn({method, url: fullUrl, headers}))

        if (typeof document !== "undefined") initProgress()

        const fetchInit: RequestInit = {method, headers, body, credentials: "include", redirect: "follow"}
        if (config.timeout) fetchInit.signal = AbortSignal.timeout(config.timeout)

        let response: Response
        try {
            response = await fetch(fullUrl, fetchInit)
        } finally {
            increaseProgress()
        }

        const requestConfig = {...config, method, url: fullUrl}
        const responseHeaders: Record<string, string> = {}
        response.headers.forEach((value, key) => {responseHeaders[key] = value})

        let responseData: unknown
        if (config.responseType === "blob") {
            responseData = await response.blob()
        } else if (response.status === 204 || response.headers.get("content-length") === "0") {
            responseData = null
        } else if (config.responseType === "text") {
            responseData = await response.text()
        } else if ((response.headers.get("content-type") ?? "").includes("application/json")) {
            responseData = await response.json()
        } else {
            responseData = await response.text()
        }

        const result: KestraHttpResponse<T> = {
            data: responseData as T,
            status: response.status,
            statusText: response.statusText,
            headers: responseHeaders,
            request: {responseURL: response.url},
            config: requestConfig,
        }

        const validateStatus = config.validateStatus ?? ((status: number) => status < 400)
        if (!validateStatus(response.status)) {
            const error: KestraHttpError = new Error(`Request failed with status code ${response.status}`)
            error.status = response.status
            error.response = result
            error.config = requestConfig
            throw handleErrorCentrally(error)
        }

        return result
    }

    const httpClient: KestraHttpClient = {
        defaults,
        interceptors: {request: {use: fn => requestInterceptors.push(fn)}},
        get: withAuthRetry((url, config) => rawRequest("GET", url, undefined, config)),
        post: withAuthRetry((url, data, config) => rawRequest("POST", url, data, config)),
        put: withAuthRetry((url, data, config) => rawRequest("PUT", url, data, config)),
        patch: withAuthRetry((url, data, config) => rawRequest("PATCH", url, data, config)),
        delete: withAuthRetry((url, config) => rawRequest("DELETE", url, undefined, config)),
    }

    // ── Generated-endpoint client (ExecutionsAPI.foo(), ...) ─────────────────
    // configureClient() wires content-type/accept/paramsSerializer/querySerializer and
    // registers its own error normalizer (sets error.status/.message). Ours runs after.
    const client = configureClient(clientConfig)

    client.interceptors.request.use((request) => {
        if (typeof document !== "undefined") initProgress()
        if (requestInterceptors.length === 0) return request
        const headers = new Headers(request.headers)
        requestInterceptors.forEach(fn => fn({method: request.method, url: request.url, headers}))
        return new Request(request, {headers})
    })

    // Fires for both successful and failed responses (the SDK checks response.ok
    // AFTER running response interceptors) - a genuine network error (no response
    // at all) skips this and is ticked from the error interceptor's no-response branch.
    client.interceptors.response.use((response) => {
        increaseProgress()
        return response
    })

    client.interceptors.error.use((error, response, request, opts) => {
        const kestraError = error as KestraHttpError
        if (!response) {
            increaseProgress()
            return kestraError // network error, no response to attach
        }

        // The SDK's own error interceptor already merged the parsed body's fields onto
        // `error` (plus .status/.message) - reconstruct them into an axios-like `data`
        // object so the many call sites reading `err.response.data.message` /
        // `err.response.data._embedded.errors` keep working unchanged.
        const data: Record<string, unknown> = {message: kestraError.message}
        for (const key of Object.keys(kestraError)) {
            if (key !== "status" && key !== "message") data[key] = (kestraError as unknown as Record<string, unknown>)[key]
        }
        const responseHeaders: Record<string, string> = {}
        response.headers.forEach((value, key) => {responseHeaders[key] = value})

        kestraError.response = {
            data,
            status: response.status,
            statusText: response.statusText,
            headers: responseHeaders,
            request: {responseURL: response.url},
            config: {
                method: request?.method ?? "",
                url: request?.url ?? "",
                showMessageOnError: (opts as {showMessageOnError?: boolean} | undefined)?.showMessageOnError,
            },
        }
        kestraError.config = kestraError.response.config

        if (kestraError.status === 400) return data as unknown as KestraHttpError

        return handleErrorCentrally(kestraError)
    })

    // client.get/post/put/patch/delete/request are looked up live off this object by
    // every generated endpoint call, so wrapping them here covers all of those too.
    const clientAny = client as unknown as Record<string, (...args: any[]) => Promise<any>>
    for (const method of ["get", "post", "put", "patch", "delete", "request"]) {
        clientAny[method] = withAuthRetry(clientAny[method].bind(client))
    }

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

    return httpClient
}
