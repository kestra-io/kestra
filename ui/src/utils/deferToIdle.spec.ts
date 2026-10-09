import {afterAll, describe, expect, it, vi} from "vitest"
import {deferToIdle} from "./deferToIdle"

describe("deferToIdle", () => {
    afterAll(() => vi.unstubAllGlobals())

    it("uses requestIdleCallback when available", () => {
        const requestIdleCallback = vi.fn()
        vi.stubGlobal("requestIdleCallback", requestIdleCallback)

        deferToIdle(() => {})

        expect(requestIdleCallback).toHaveBeenCalledWith(
            expect.any(Function),
            {timeout: 2000},
        )
    })

    it("forwards a custom timeout to requestIdleCallback", () => {
        const requestIdleCallback = vi.fn()
        vi.stubGlobal("requestIdleCallback", requestIdleCallback)

        deferToIdle(() => {}, 5000)

        expect(requestIdleCallback).toHaveBeenCalledWith(
            expect.any(Function),
            {timeout: 5000},
        )
    })

    it("cancels the requestIdleCallback when the returned function is called", () => {
        const requestIdleCallback = vi.fn(() => 123)
        const cancelIdleCallback = vi.fn()

        vi.stubGlobal("requestIdleCallback", requestIdleCallback)
        vi.stubGlobal("cancelIdleCallback", cancelIdleCallback)

        const cancel = deferToIdle(() => {})
        cancel()

        expect(cancelIdleCallback).toHaveBeenCalledWith(123)
    })

    it("uses setTimeout when requestIdleCallback is unavailable", () => {
        vi.stubGlobal("requestIdleCallback", undefined)

        const setTimeoutSpy = vi.spyOn(globalThis, "setTimeout")

        deferToIdle(() => {})

        expect(setTimeoutSpy).toHaveBeenCalledWith(
            expect.any(Function),
            0,
        )

        setTimeoutSpy.mockRestore()
    })

    it("cancels the setTimeout when the returned function is called", () => {
        vi.stubGlobal("requestIdleCallback", undefined)

        const clearTimeoutSpy = vi.spyOn(globalThis, "clearTimeout")

        const cancel = deferToIdle(() => {})
        cancel()

        expect(clearTimeoutSpy).toHaveBeenCalled()

        clearTimeoutSpy.mockRestore()
    })

})
