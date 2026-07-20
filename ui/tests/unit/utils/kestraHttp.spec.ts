import {beforeEach, describe, expect, it, vi} from "vitest"
import {useClient} from "@kestra-io/kestra-sdk"

import {setupKestraHttp} from "../../../src/utils/kestraHttp"

vi.mock("nprogress", () => ({
    default: {
        start: vi.fn(),
        done: vi.fn(),
        set: vi.fn(),
    },
}))

describe("Kestra HTTP error handling", () => {
    const onError = vi.fn()

    beforeEach(() => {
        onError.mockReset()
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
            JSON.stringify({message: "Not Found"}),
            {
                status: 404,
                headers: {"content-type": "application/json"},
            },
        )))
    })

    it("keeps expected 404 responses out of the global error state", async () => {
        setupKestraHttp({}, {onError})

        await expect(useClient().get("https://example.test/optional", {showMessageOnError: false})).rejects.toMatchObject({status: 404})
        expect(onError).not.toHaveBeenCalled()
    })

    it("continues to report unhandled 404 responses globally", async () => {
        setupKestraHttp({}, {onError})

        await expect(useClient().get("https://example.test/missing")).rejects.toMatchObject({status: 404})
        expect(onError).toHaveBeenCalledOnce()
        expect(onError).toHaveBeenCalledWith("error", expect.objectContaining({status: 404}))
    })
})
