import {describe, it, expect, vi, beforeEach} from "vitest"

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
    fakeAxiosClient: {get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()},
    nprogressStart: vi.fn(),
    nprogressSet: vi.fn(),
    nprogressDone: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    configureClient: vi.fn(() => fakeClient),
    useClient: vi.fn(() => fakeAxiosClient),
}))

vi.mock("nprogress", () => ({
    default: {start: nprogressStart, set: nprogressSet, done: nprogressDone},
}))

import {setupKestraHttp} from "../../../src/utils/kestraHttp"

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
        nprogressDone.mockClear()
    })

    it("settles the progress counter via afterEach on a normal navigation", async () => {
        setupKestraHttp({}, {router: router as any})

        beforeEachCb()
        afterEachCb()
        await new Promise((r) => setTimeout(r, 60))

        expect(nprogressDone).toHaveBeenCalledTimes(1)
    })

    it("settles the progress counter via onError when a navigation throws instead of completing", async () => {
        setupKestraHttp({}, {router: router as any})

        // A guard throwing, or a failed async-component chunk import, rejects the
        // navigation and never calls afterEach - onError is the only place left to
        // settle the counter and unstick the loading bar.
        beforeEachCb()
        onErrorCb()
        await new Promise((r) => setTimeout(r, 60))

        expect(nprogressDone).toHaveBeenCalledTimes(1)
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

        onErrorInterceptor(Object.assign(new Error("404 Not Found"), {status: 404}), notFoundResponse, request, opts)

        return coreStore
    }

    beforeEach(() => {
        vi.spyOn(console, "error").mockImplementation(() => {})
    })

    it("shows the failed request as a toast and logs it, instead of swapping the page for the not-found screen", () => {
        const coreStore = triggerNotFound()

        expect(coreStore.message).toMatchObject({
            variant: "error",
            response: {status: 404, config: {method: "get", url: "/api/v1/main/flows/io.kestra/missing"}},
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
