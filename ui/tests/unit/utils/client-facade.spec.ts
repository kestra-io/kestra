import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {createClientFacade} from "../../../packages/kestra-sdk/src/client-facade"

describe("createClientFacade", () => {
    const errorFn = vi.fn((error: unknown) => error)
    const client = {
        interceptors: {
            request: {fns: []},
            response: {fns: []},
            error: {fns: [errorFn]},
        },
    }
    const formDataBodySerializer = {bodySerializer: (body: unknown) => body}

    beforeEach(() => {
        errorFn.mockClear()
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it("resolves normally on a successful response", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
            new Response(JSON.stringify({ok: true}), {status: 200, headers: {"content-type": "application/json"}}),
        ))
        const {useClient} = createClientFacade(client, formDataBodySerializer)

        const result = await useClient().get("https://example.test/api")

        expect(result.data).toEqual({ok: true})
        expect(errorFn).not.toHaveBeenCalled()
    })

    it("still runs client.interceptors.error.fns when fetch itself rejects (network failure)", async () => {
        const networkError = new TypeError("Failed to fetch")
        vi.stubGlobal("fetch", vi.fn().mockRejectedValue(networkError))
        const {useClient} = createClientFacade(client, formDataBodySerializer)

        await expect(useClient().get("https://example.test/api")).rejects.toBe(networkError)

        // This is the fix under test: without it, a fetch()-level rejection bypasses
        // client.interceptors.error.fns entirely, so the app's NProgress-completing
        // error interceptor never runs and the loading indicator gets stuck.
        expect(errorFn).toHaveBeenCalledTimes(1)
        expect(errorFn).toHaveBeenCalledWith(networkError, undefined, expect.any(Request), expect.anything())
    })
})
