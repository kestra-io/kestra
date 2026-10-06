import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"

// vi.mock(...) below is hoisted above these declarations, so the fixtures it
// references must come from vi.hoisted() rather than plain top-level consts.
const {fakeClient, fakeAxiosClient, nprogressStart, nprogressSet, nprogressDone} = vi.hoisted(() => ({
    fakeClient: {
        interceptors: {
            request: {use: vi.fn()},
            response: {use: vi.fn()},
            error: {use: vi.fn()},
        },
        get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn(), request: vi.fn(),
    },
    fakeAxiosClient: {get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn(), stream: vi.fn()},
    nprogressStart: vi.fn(),
    nprogressSet: vi.fn(),
    nprogressDone: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    configureClient: vi.fn(() => fakeClient),
    useClient: vi.fn(() => fakeAxiosClient),
    // Stands in for the real helper: recognises a problem document the SDK flattened onto the Error.
    asProblem: (error: Record<string, unknown>) => (error?.type
        ? {type: error.type, title: error.title, detail: error.detail, status: error.status}
        : undefined),
}))

vi.mock("nprogress", () => ({
    default: {start: nprogressStart, set: nprogressSet, done: nprogressDone},
}))

import type {Router} from "vue-router"
import {isReportedCentrally, setupKestraHttp, type KestraHttpError} from "../../../src/utils/kestraHttp"
import {markServerReachable, markServerUnreachable, UNREACHABLE_DELAY, useServerReachability} from "../../../src/composables/useServerReachability"
import {isReauthOpen, requestReauth, resolveReauth} from "../../../src/composables/useReauthDialog"

describe("setupKestraHttp router NProgress hooks", () => {
    let beforeEachCb: () => void
    let afterEachCb: () => void
    let onErrorCb: () => void
    const router = {
        beforeEach: vi.fn((cb: () => void) => { beforeEachCb = cb }),
        afterEach: vi.fn((cb: () => void) => { afterEachCb = cb }),
        onError: vi.fn((cb: () => void) => { onErrorCb = cb }),
    }

    beforeEach(() => {
        nprogressStart.mockClear()
        nprogressSet.mockClear()
        nprogressDone.mockClear()
    })

    it("settles the progress counter via afterEach on a normal navigation", async () => {
        setupKestraHttp({}, {router: router as unknown as Router})

        beforeEachCb()
        afterEachCb()
        await new Promise((r) => setTimeout(r, 60))

        expect(nprogressDone).toHaveBeenCalledTimes(1)
    })

    it("settles the progress counter via onError when a navigation throws instead of completing", async () => {
        setupKestraHttp({}, {router: router as unknown as Router})

        // A guard throwing, or a failed async-component chunk import, rejects the
        // navigation and never calls afterEach - onError is the only place left to
        // settle the counter and unstick the loading bar.
        beforeEachCb()
        onErrorCb()
        await new Promise((r) => setTimeout(r, 60))

        expect(nprogressDone).toHaveBeenCalledTimes(1)
    })

    it("opts stream() out of NProgress, matching generated sse methods", async () => {
        const innerStream = vi.fn().mockResolvedValue(new Response())
        fakeAxiosClient.stream = innerStream
        setupKestraHttp({})

        await fakeAxiosClient.stream("/stream", {id: 1}, {signal: new AbortController().signal})

        expect(innerStream).toHaveBeenCalledWith(
            "/stream",
            {id: 1},
            expect.objectContaining({__kestraSkipProgress: true}),
        )
    })

    it("does not start NProgress when a request opts out with __kestraSkipProgress", async () => {
        setupKestraHttp({})
        const onRequest = fakeClient.interceptors.request.use.mock.calls.at(-1)![0]
        const onResponse = fakeClient.interceptors.response.use.mock.calls.at(-1)![0]
        const onError = fakeClient.interceptors.error.use.mock.calls.at(-1)![0]
        const skip = {__kestraSkipProgress: true}

        onRequest(new Request("http://example.test/x"), skip)
        onResponse(new Response(), new Request("http://example.test/x"), skip)
        onError(new DOMException("Aborted", "AbortError"), undefined, new Request("http://example.test/x"), skip)
        await new Promise((r) => setTimeout(r, 60))

        expect(nprogressStart).not.toHaveBeenCalled()
        expect(nprogressDone).not.toHaveBeenCalled()
    })
})

describe("setupKestraHttp request headers", () => {
    it("marks same-origin requests as scripted so the backend skips the WWW-Authenticate challenge", () => {
        setupKestraHttp({})
        const onRequest = fakeClient.interceptors.request.use.mock.calls.at(-1)![0]

        const request = onRequest(new Request(`${window.location.origin}/api/v1/x`, {headers: {Accept: "application/json"}}), {})

        expect(request.headers.get("X-Requested-With")).toBe("XMLHttpRequest")
        expect(request.headers.get("Accept")).toBe("application/json")
    })

    it("leaves cross-origin requests alone so they never trigger a CORS preflight", () => {
        setupKestraHttp({})
        const onRequest = fakeClient.interceptors.request.use.mock.calls.at(-1)![0]

        const request = onRequest(new Request("https://api.example.test/v1/x"), {})

        expect(request.headers.has("X-Requested-With")).toBe(false)
    })
})

describe("setupKestraHttp central 404 handling", () => {
    const notFoundResponse = {
        status: 404,
        statusText: "Not Found",
        url: "http://localhost:8080/api/v1/main/flows/io.kestra/missing",
        headers: {forEach: () => {}},
    }
    const request = {method: "get", url: "/api/v1/main/flows/io.kestra/missing"}

    function triggerNotFound(opts?: Record<string, unknown>) {
        const coreStore = {message: undefined as unknown, error: undefined as unknown}
        setupKestraHttp({}, {coreStore})
        const onErrorInterceptor = fakeClient.interceptors.error.use.mock.calls.at(-1)![0]

        const notFound = Object.assign(new Error("404 Not Found"), {
            status: 404,
            type: "https://kestra.io/problems/not-found",
            title: "Resource not found",
            detail: "Flow io.kestra.missing not found",
        })
        onErrorInterceptor(notFound, notFoundResponse, request, opts)

        return coreStore
    }

    beforeEach(() => {
        vi.spyOn(console, "error").mockImplementation(() => {})
    })

    it("shows the failed request as a toast and logs it, instead of swapping the page for the not-found screen", () => {
        const coreStore = triggerNotFound()

        expect(coreStore.message).toMatchObject({
            variant: "error",
            status: 404,
            request: {method: "get", url: "/api/v1/main/flows/io.kestra/missing"},
            problem: {type: "https://kestra.io/problems/not-found", detail: "Flow io.kestra.missing not found"},
        })
        // The full-page error screen is driven by coreStore.error - a 404 must not reach it.
        expect(coreStore.error).toBeUndefined()
        expect(console.error).toHaveBeenCalledWith(
            expect.stringContaining("GET /api/v1/main/flows/io.kestra/missing failed with 404"),
            expect.anything(),
        )
    })

    it("stays silent for callers that opted out with ignoreNotFound or showMessageOnError", () => {
        expect(triggerNotFound({ignoreNotFound: true}).message).toBeUndefined()
        expect(triggerNotFound({showMessageOnError: false}).message).toBeUndefined()
    })
})

describe("isReportedCentrally", () => {
    const failure = (status: number, config?: Record<string, unknown>) => ({
        status,
        response: {data: {title: "problem"}},
        config: {method: "put", url: "/api/v1/main/flows/io.kestra/gone", ...config},
    }) as KestraHttpError

    // What a caller reporting its own failures asks before adding a toast of its own: the flow
    // editor's save handler would otherwise duplicate the global toast on a deleted flow.
    it("claims the failures the interceptor toasts and leaves the caller the rest", () => {
        expect(isReportedCentrally(failure(404))).toBe(true)
        expect(isReportedCentrally(failure(500))).toBe(true)

        expect(isReportedCentrally(failure(400))).toBe(false)
        expect(isReportedCentrally(failure(401))).toBe(false)
        expect(isReportedCentrally(failure(404, {ignoreNotFound: true}))).toBe(false)
        expect(isReportedCentrally(failure(500, {showMessageOnError: false}))).toBe(false)
        expect(isReportedCentrally({status: 0} as KestraHttpError)).toBe(false)
    })
})

describe("setupKestraHttp server reachability", () => {
    const kestraApiRequest = () => new Request(`${window.location.origin}/api/v1/x`)

    function interceptors() {
        setupKestraHttp({}, {})
        return {
            onResponse: fakeClient.interceptors.response.use.mock.calls.at(-1)![0],
            onError: fakeClient.interceptors.error.use.mock.calls.at(-1)![0],
        }
    }

    beforeEach(() => {
        vi.useFakeTimers()
        markServerReachable()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("flags the server unreachable once a response-less failure outlasts the delay, and clears it on the next response", () => {
        const {onResponse, onError} = interceptors()
        const {unreachable} = useServerReachability()

        onError(new TypeError("Failed to fetch"), undefined, kestraApiRequest(), {})
        expect(unreachable.value).toBe(false)
        vi.advanceTimersByTime(UNREACHABLE_DELAY)
        expect(unreachable.value).toBe(true)

        onResponse({status: 200}, kestraApiRequest(), {})
        expect(unreachable.value).toBe(false)
    })

    it("does not flag a lone failure that a response clears within the delay, such as a request cut by a page unload", () => {
        const {onResponse, onError} = interceptors()

        onError(new TypeError("Failed to fetch"), undefined, kestraApiRequest(), {})
        onResponse({status: 200}, kestraApiRequest(), {})
        vi.advanceTimersByTime(UNREACHABLE_DELAY)

        expect(useServerReachability().unreachable.value).toBe(false)
    })

    it("ignores failures and successes of requests that are not Kestra API calls", () => {
        const {onResponse, onError} = interceptors()
        const {unreachable} = useServerReachability()

        onError(new TypeError("Failed to fetch"), undefined, new Request("https://api.example.test/v1/reports/events"), {})
        vi.advanceTimersByTime(UNREACHABLE_DELAY)
        expect(unreachable.value).toBe(false)

        markServerUnreachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY)
        onResponse({status: 200}, new Request("https://api.example.test/v1/feeds"), {})
        expect(unreachable.value).toBe(true)
    })

    it("flags the server unreachable when a gateway answers 503 in its place", () => {
        const {onError} = interceptors()
        const gatewayResponse = {status: 503, statusText: "Service Unavailable", url: "http://x/api", headers: {forEach: () => {}}}

        onError(Object.assign(new Error("503"), {status: 503}), gatewayResponse, kestraApiRequest(), {})
        vi.advanceTimersByTime(UNREACHABLE_DELAY)

        expect(useServerReachability().unreachable.value).toBe(true)
    })

    it("ignores a request the caller aborted", () => {
        const {onError} = interceptors()
        const controller = new AbortController()
        controller.abort()

        onError(new DOMException("aborted", "AbortError"), undefined, new Request(`${window.location.origin}/api/v1/x`, {signal: controller.signal}), {})
        vi.advanceTimersByTime(UNREACHABLE_DELAY)

        expect(useServerReachability().unreachable.value).toBe(false)
    })
})

describe("setupKestraHttp 401 retry", () => {
    it("replays the failed request once onUnauthorized reports a successful re-authentication", async () => {
        const unauthorized = Object.assign(new Error("401"), {status: 401})
        const get = vi.fn().mockRejectedValueOnce(unauthorized).mockResolvedValueOnce({data: "ok"})
        fakeAxiosClient.get = get
        const onUnauthorized = vi.fn().mockResolvedValue(true)

        setupKestraHttp({}, {isLoggedIn: () => false, onUnauthorized})

        await expect(fakeAxiosClient.get("/executions", {q: 1})).resolves.toEqual({data: "ok"})
        expect(onUnauthorized).toHaveBeenCalledTimes(1)
        expect(get).toHaveBeenCalledTimes(2)
    })

    it("replays a 401 that was in flight while the user signed back in, without opening the dialog again", async () => {
        const unauthorized = Object.assign(new Error("401"), {status: 401})
        let loggedIn = false
        const get = vi.fn().mockImplementationOnce(async () => {
            loggedIn = true
            throw unauthorized
        }).mockResolvedValueOnce({data: "ok"})
        fakeAxiosClient.get = get
        const onUnauthorized = vi.fn()

        setupKestraHttp({}, {isLoggedIn: () => loggedIn, onUnauthorized})

        await expect(fakeAxiosClient.get("/executions")).resolves.toEqual({data: "ok"})
        expect(onUnauthorized).not.toHaveBeenCalled()
        expect(get).toHaveBeenCalledTimes(2)
    })

    it("does not replay a 401 for a session that was already signed in when the request left", async () => {
        const unauthorized = Object.assign(new Error("401"), {status: 401})
        const get = vi.fn().mockRejectedValue(unauthorized)
        fakeAxiosClient.get = get

        setupKestraHttp({}, {isLoggedIn: () => true})

        await expect(fakeAxiosClient.get("/executions")).rejects.toBe(unauthorized)
        expect(get).toHaveBeenCalledTimes(1)
    })
})

describe("setupKestraHttp session recovery", () => {
    afterEach(() => {
        resolveReauth(false)
    })

    it("closes the dialog when an API call succeeds and the session is confirmed, as after a sign-in in another tab", async () => {
        setupKestraHttp({}, {})
        const onResponse = fakeClient.interceptors.response.use.mock.calls.at(-1)![0]
        const outcome = requestReauth({loginUrl: "/ui/login", confirm: vi.fn().mockResolvedValue(undefined)})

        onResponse({status: 200}, new Request(`${window.location.origin}/api/v1/x`), {})

        await expect(outcome).resolves.toBe(true)
    })

    it("keeps the dialog open when a successful call does not prove the session is back", async () => {
        setupKestraHttp({}, {})
        const onResponse = fakeClient.interceptors.response.use.mock.calls.at(-1)![0]
        const confirm = vi.fn().mockRejectedValue(new Error("anonymous endpoint"))
        requestReauth({loginUrl: "/ui/login", confirm})

        onResponse({status: 200}, new Request(`${window.location.origin}/api/v1/configs`), {})
        await vi.waitFor(() => expect(confirm).toHaveBeenCalled())

        expect(isReauthOpen()).toBe(true)
    })
})
