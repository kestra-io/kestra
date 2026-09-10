import NProgress from "nprogress"
import type {Router} from "vue-router"
import {configureClient, useClient, asProblem, type ProblemDetail} from "@kestra-io/kestra-sdk"

let pendingRoute = false
let requestsTotal = 0
let requestsCompleted = 0

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

export interface KestraHttpError extends Error {
    status?: number
    /**
     * The RFC 9457 problem document, when the failure came from the Kestra API — which is every error the
     * app raises against it. Prefer this, or the `asProblem` helper, over digging into `response.data`.
     */
    problem?: ProblemDetail
    response?: {
        /**
         * The parsed body: the problem document for any API error, an arbitrary body otherwise.
         *
         * Deliberately `unknown` rather than `any` so it cannot be dereferenced without a narrowing step
         * — but note that a `catch (e: any)` call site defeats that, so the `noLegacyErrorFields` unit
         * test is what actually keeps reads of the removed `message`/`_embedded`/`invalids` fields out.
         * Use `problem`, or the `asProblem` helper, instead of narrowing this by hand.
         */
        data: unknown
        status: number
        statusText: string
        headers: Record<string, string>
        request: {responseURL: string}
        config: {method: string; url: string; showMessageOnError?: boolean; ignoreNotFound?: boolean}
    }
    config?: {method: string; url: string; showMessageOnError?: boolean; ignoreNotFound?: boolean}
}

/**
 * Whether this failure raises the global error toast, so a caller that reports failures itself can
 * skip the ones already on screen. A 400 or a 401 is left to the caller, as is a failure with no
 * response body, and so is anything the request opted out of.
 */
export function isReportedCentrally(error: KestraHttpError): boolean {
    if (error.config?.showMessageOnError === false) return false
    if (error.status === 404) return error.config?.ignoreNotFound !== true
    return error.status !== 401 && error.status !== 400 && Boolean(error.response?.data)
}

/**
 * Per-request options the interceptors above read. Declared here rather than derived from the
 * SDK's own option type, which is bound to one edition's generated client.
 */
export interface KestraRequestOptions {
    /** `false` silences the error toast, leaving the caller to report the failure. */
    showMessageOnError?: boolean
    /** Marks a 404 as an expected outcome the caller handles itself. */
    ignoreNotFound?: boolean
}

/**
 * Rebuilds an axios-like `data` object from an Error the SDK flattened a non-problem body onto. Only
 * reached for responses from outside the API surface.
 */
function legacyData(error: KestraHttpError): Record<string, unknown> {
    const data: Record<string, unknown> = {message: error.message}
    for (const key of Object.keys(error)) {
        if (key !== "status" && key !== "message" && key !== "problem") {
            data[key] = (error as unknown as Record<string, unknown>)[key]
        }
    }
    return data
}

export interface KestraHttpOptions {
    router?: Router
    coreStore?: {message: unknown}
    beforeLogout?: () => void
    isLoggedIn?: () => boolean
    onError?: (error: unknown) => void
    onUnauthorized?: (navigateToLogin: () => void, error: KestraHttpError) => Promise<boolean> | boolean | void
}

export function setupKestraHttp(
    clientConfig: Record<string, unknown> = {},
    options: KestraHttpOptions = {},
): ReturnType<typeof configureClient> {
    const {
        router,
        coreStore,
        beforeLogout,
        isLoggedIn = () => false,
        onError = (error: unknown) => {
            if (!coreStore) return
            const kestraError = error as KestraHttpError
            coreStore.message = {
                variant: "error",
                problem: kestraError.problem,
                status: kestraError.response?.status,
                request: {
                    method: kestraError.response?.config.method ?? "GET",
                    url: kestraError.response?.config.url ?? "unknown url",
                },
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
        if (!isReportedCentrally(error)) return error

        if (error.status === 404) {
            // A 404 is reported where it happened rather than by swapping the page for the
            // not-found screen: that hid which request failed and left no way back.
            console.error(`${(error.config?.method ?? "GET").toUpperCase()} ${error.config?.url ?? ""} failed with 404`, error)
        }
        onError(error)

        return error
    }

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

    const sse = (client as unknown as {sse?: Record<string, (streamOptions: Record<string, unknown>) => unknown>}).sse
    for (const method of Object.keys(sse ?? {})) {
        const streamFn = sse![method].bind(sse)
        sse![method] = (streamOptions) => streamFn({...streamOptions, [SKIP_PROGRESS]: true})
    }

    client.interceptors.request.use((request, opts: unknown) => {
        if (typeof document !== "undefined" && !(opts as Record<string, unknown>)?.[SKIP_PROGRESS]) initProgress()
        return request
    })

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

        // An API error is a problem document, and `response.data` IS that document — the same value the
        // useClient facade attaches, so both call paths finally expose one identical shape. Anything else
        // came from outside the API surface (Micronaut's own responses, the Apps error layout, plain text);
        // keep reconstructing those from the flattened Error so they still reach their call sites.
        const problem = asProblem(kestraError)
        const data: Record<string, unknown> = problem
            ? (problem as unknown as Record<string, unknown>)
            : legacyData(kestraError)

        const responseHeaders: Record<string, string> = {}
        response.headers.forEach((value, key) => {responseHeaders[key] = value})

        kestraError.problem = problem
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
                ignoreNotFound: (opts as {ignoreNotFound?: boolean} | undefined)?.ignoreNotFound,
            },
        }
        kestraError.config = kestraError.response.config

        // A 400 rejects like any other error, so `instanceof Error`, `.status` and `.response` all hold on
        // the status the bulk endpoints use. handleErrorCentrally still keeps it out of the global toast.
        return handleErrorCentrally(kestraError)
    })

    for (const target of [client, useClient()] as const) {
        const targetAny = target as unknown as Record<string, (...args: any[]) => Promise<any>>
        for (const method of ["get", "post", "put", "patch", "delete", "request", "stream"]) {
            if (typeof targetAny[method] === "function") targetAny[method] = withAuthRetry(targetAny[method].bind(target))
        }
    }

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
