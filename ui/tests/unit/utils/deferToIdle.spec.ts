import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {deferToIdle} from "../../../src/utils/deferToIdle"

describe("deferToIdle", () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
    })

    describe("when requestIdleCallback is supported", () => {
        let requestIdleCallbackMock: ReturnType<typeof vi.fn>
        let cancelIdleCallbackMock: ReturnType<typeof vi.fn>

        beforeEach(() => {
            requestIdleCallbackMock = vi.fn((_cb: () => void, _options?: {timeout?: number}) => 42)
            cancelIdleCallbackMock = vi.fn()

            vi.stubGlobal("requestIdleCallback", requestIdleCallbackMock)
            vi.stubGlobal("cancelIdleCallback", cancelIdleCallbackMock)
        })

        afterEach(() => {
            vi.unstubAllGlobals()
        })

        it("uses requestIdleCallback with default timeout of 2000", () => {
            const callback = vi.fn()
            deferToIdle(callback)

            expect(requestIdleCallbackMock).toHaveBeenCalledTimes(1)
            expect(requestIdleCallbackMock).toHaveBeenCalledWith(callback, {timeout: 2000})
        })

        it("forwards a custom timeout option", () => {
            const callback = vi.fn()
            deferToIdle(callback, 5000)

            expect(requestIdleCallbackMock).toHaveBeenCalledWith(callback, {timeout: 5000})
        })

        it("cancels pending idle callback using returned cancel function", () => {
            const callback = vi.fn()
            const cancel = deferToIdle(callback)

            cancel()
            expect(cancelIdleCallbackMock).toHaveBeenCalledWith(42)
        })
    })

    describe("when requestIdleCallback is absent (fallback path)", () => {
        it("schedules callback with setTimeout and executes it", () => {
            const callback = vi.fn()
            deferToIdle(callback)

            expect(callback).not.toHaveBeenCalled()
            vi.runAllTimers()
            expect(callback).toHaveBeenCalledTimes(1)
        })

        it("cancels pending callback on the setTimeout fallback path", () => {
            const callback = vi.fn()
            const cancel = deferToIdle(callback)

            cancel()
            vi.runAllTimers()
            expect(callback).not.toHaveBeenCalled()
        })
    })
})
