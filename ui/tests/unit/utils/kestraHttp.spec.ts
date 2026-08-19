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

describe("setupKestraHttp 404 diagnostics", () => {
    type ErrorInterceptor = (error: any, response: any, request: any, opts?: any) => any

    const captureErrorInterceptor = (coreStore: Record<string, unknown>): ErrorInterceptor => {
        fakeClient.interceptors.error.use.mockClear()
        setupKestraHttp({}, {coreStore: coreStore as any})
        return fakeClient.interceptors.error.use.mock.calls.at(-1)![0] as ErrorInterceptor
    }

    const missingFlowPath = "/api/v1/main/flows/my.namespace/missing"
    const missingFlowUrl = window.location.origin + missingFlowPath

    const notFoundResponse = {
        status: 404,
        statusText: "Not Found",
        url: missingFlowUrl,
        headers: {forEach: () => {}},
    }

    const notFoundRequest = {
        method: "get",
        url: missingFlowUrl,
    }

    it("records the request that 404ed so the error page can name it", () => {
        const coreStore: Record<string, unknown> = {}
        const onError = captureErrorInterceptor(coreStore)

        onError(Object.assign(new Error("Not Found"), {status: 404}), notFoundResponse, notFoundRequest)

        expect(coreStore.error).toBe(404)
        expect(coreStore.failedRequest).toEqual({
            status: 404,
            method: "GET",
            url: missingFlowPath,
            message: "Not Found",
        })
    })

    it("leaves the error page untouched when the caller handles the 404 itself", () => {
        const coreStore: Record<string, unknown> = {}
        const onError = captureErrorInterceptor(coreStore)

        onError(Object.assign(new Error("Not Found"), {status: 404}), notFoundResponse, notFoundRequest, {ignoreNotFound: true})

        expect(coreStore.error).toBeUndefined()
        expect(coreStore.failedRequest).toBeUndefined()
    })
})
