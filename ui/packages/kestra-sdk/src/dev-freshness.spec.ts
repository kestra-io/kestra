import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"

describe("warnIfSdkStale", () => {
    beforeEach(() => {
        vi.resetModules()
        vi.restoreAllMocks()
    })

    afterAll(() => vi.unstubAllGlobals())

    it("bypasses the HTTP cache when fetching the live spec", async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            redirected: false,
            arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
        })
        vi.stubGlobal("fetch", fetchMock)

        const {warnIfSdkStale} = await import("./dev-freshness")
        await warnIfSdkStale("abcdef0123456789", "http://backend/swagger/kestra.yml")

        expect(fetchMock).toHaveBeenCalledWith(
            "http://backend/swagger/kestra.yml",
            expect.objectContaining({cache: "no-store"}),
        )
    })

    it("does not warn when the request was redirected (e.g. unauthenticated, redirected to login)", async () => {
        const dispatchSpy = vi.spyOn(window, "dispatchEvent")
        const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {})
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
            ok: true,
            redirected: true,
            arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
        }))

        const {warnIfSdkStale} = await import("./dev-freshness")
        await warnIfSdkStale("abcdef0123456789", "http://backend/swagger/kestra.yml")

        expect(warnSpy).not.toHaveBeenCalled()
        expect(dispatchSpy).not.toHaveBeenCalled()
    })

    it("does not consume the one-shot check on a redirected response, so a later call can still succeed", async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce({ok: true, redirected: true, arrayBuffer: () => Promise.resolve(new ArrayBuffer(0))})
            .mockResolvedValueOnce({ok: true, redirected: false, arrayBuffer: () => Promise.resolve(new ArrayBuffer(0))})
        vi.stubGlobal("fetch", fetchMock)

        const {warnIfSdkStale} = await import("./dev-freshness")
        await warnIfSdkStale("abcdef0123456789", "http://backend/swagger/kestra.yml")
        await warnIfSdkStale("abcdef0123456789", "http://backend/swagger/kestra.yml")

        expect(fetchMock).toHaveBeenCalledTimes(2)
    })
})
