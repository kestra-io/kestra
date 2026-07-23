import NProgress from "nprogress"
import type {Router} from "vue-router"
import {configureClient, useClient} from "@kestra-io/kestra-sdk"

// ── NProgress helpers ────────────────────────────────────────────────────────

let pendingRoute = false
let requestsTotal = 0
let requestsCompleted = 0

/** Request-option flag marking a request that must be left out of progress accounting. */
const SKIP_PROGRESS = "__kestraSkipProgress"

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

export interface KestraHttpError extends Error {
    status?: number
    response?: {
        data: any
        status: number
        statusText: string
        headers: Record<string, string>
        request: {responseURL: string}
        config: {method: string; url: string; showMessageOnError?: boolean}
    }
    config?: {method: string; url: string; showMessageOnError?: boolean}
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

/**
 * Configures the shared fetch client both generated SDK endpoint calls and useClient()'s
 * ad-hoc calls go through, wiring NProgress, centralized 401/404/400 error handling, and a
 * 401-retry hook (EE plugs in silent token refresh). configureClient()'s own request/response/
 * error interceptors normalize content-type/accept and error status/message before ours run,
 * and cover useClient() too - it shares client.interceptors under the hood.
 *
 * Returns the generated-endpoint client, so callers can register extra per-request behavior
 * (e.g. a CSRF header) via `client.interceptors.request.use(...)` - that also reaches
 * useClient() calls. Bind `useClient()` itself to `$http` for ad-hoc calls.
 */
export function setupKestraHttp(
    clientConfig: Record<string, unknown> = {},
    options: KestraHttpOptions = {},
): ReturnType<typeof configureClient> {
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

    function navigateToLogin() {
        if (!router) return
        const currentPath = window.location.pathname
        router.push({
            name: "login",
            query: currentPath.includes("/login") ? {} : {from: currentPath},
        })
    }

    function handleErrorCentrally(error: KestraHttpError): KestraHttpError {
        const status = error.status
        if (status === 404) {
            if (error.config?.showMessageOnError !== false) {
                onError("error", error)
            }
        } else if (status !== 401 && status !== 400 && error.response?.data && error.config?.showMessageOnError !== false) {
            onError("message", error)
        }
        return error
    }

    // Wraps get/post/put/patch/delete (and the SDK's client.request) so a 401 triggers
    // onUnauthorized and, if it resolves truthy, retries the ORIGINAL call exactly once
    // (re-invoking the unwrapped function with the same arguments) - no config
    // serialization/replay needed, no risk of retry loops since the retry bypasses this
    // wrapper.
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

    const client = configureClient(clientConfig)

    // Long-lived SSE streams must not participate in progress accounting: the SDK runs request
    // interceptors for them (createSseClient calls them from its onRequest hook) but never the
    // response/error ones, so such a request would stay pending forever and pin the bar open.
    // Tagging the whole `sse` namespace covers every current and future stream endpoint.
    const sse = (client as unknown as {sse?: Record<string, (streamOptions: Record<string, unknown>) => unknown>}).sse
    for (const method of Object.keys(sse ?? {})) {
        const streamFn = sse![method].bind(sse)
        sse![method] = (streamOptions) => streamFn({...streamOptions, [SKIP_PROGRESS]: true})
    }

    client.interceptors.request.use((request, opts: unknown) => {
        if (typeof document !== "undefined" && !(opts as Record<string, unknown>)?.[SKIP_PROGRESS]) initProgress()
        return request
    })

    // Fires for both successful and failed responses (the SDK checks response.ok AFTER
    // running response interceptors) - a genuine network error (no response) is ticked
    // from the error interceptor's no-response branch instead.
    client.interceptors.response.use((response) => {
        increaseProgress()
        return response
    })

    client.interceptors.error.use((error, response, request, opts) => {
        const kestraError = error as KestraHttpError
        if (!response) {
            increaseProgress()
            return kestraError
        }

        // The SDK's own error interceptor already merged the parsed body's fields onto
        // `error` (plus .status/.message) - reconstruct them into an axios-like `data`
        // object so call sites reading `err.response.data.message` keep working.
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

    // client.get/post/put/patch/delete/request and useClient()'s get/post/put/patch/delete
    // are looked up live off these objects by every caller, so wrapping the methods in
    // place here covers both paths for every future call, no matter where it's made from.
    for (const target of [client, useClient()] as const) {
        const targetAny = target as unknown as Record<string, (...args: any[]) => Promise<any>>
        for (const method of ["get", "post", "put", "patch", "delete", "request", "stream"]) {
            if (typeof targetAny[method] === "function") targetAny[method] = withAuthRetry(targetAny[method].bind(target))
        }
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
    // A thrown guard error or failed async-component import rejects the navigation
    // without ever calling afterEach, leaving requestsTotal permanently ahead and the
    // loading bar stuck - settle the counter here too, same as afterEach does.
    router?.onError(() => {
        if (pendingRoute) {
            increaseProgress()
            pendingRoute = false
        }
    })

    return client
}
