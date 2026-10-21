import {afterAll, afterEach, describe, expect, it, vi} from "vitest"
import {PublicApiError, publicFetch, publicFetchJson} from "./publicApi"

const fetch = vi.fn<typeof globalThis.fetch>()
vi.stubGlobal("fetch", fetch)

describe("publicApi", () => {
    afterEach(() => fetch.mockReset())
    afterAll(() => vi.unstubAllGlobals())

    /** fetch resolves on a 404, so without this the docs page would render the error body as markdown. */
    it("rejects with the status on a non-2xx response", async () => {
        fetch.mockResolvedValue(new Response("missing", {status: 404}))

        await expect(publicFetchJson("https://api.test/v1/docs")).rejects.toSatisfy(
            (error: unknown) => error instanceof PublicApiError && error.status === 404,
        )
    })

    it("posts a JSON body with cookies only when asked, dropping undefined params", async () => {
        fetch.mockResolvedValue(new Response())

        await publicFetch("https://api.test/v1/events", {withCredentials: true, body: {a: 1}, params: {q: "x", skip: undefined}})

        const [url, init] = fetch.mock.calls[0]
        expect(String(url)).toBe("https://api.test/v1/events?q=x")
        expect(init).toMatchObject({method: "POST", credentials: "include", body: "{\"a\":1}", headers: {"Content-Type": "application/json"}})
    })
})
