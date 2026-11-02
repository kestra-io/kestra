import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"

import {
    useServerReachability,
    markServerUnreachable,
    markServerReachable,
    UNREACHABLE_DELAY,
} from "./useServerReachability"

describe("useServerReachability", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        markServerReachable()
    })

    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
    })

    it("unreachable starts false", () => {
        const {unreachable} = useServerReachability()

        expect(unreachable.value).toBe(false)
    })

    it("markServerUnreachable does not flip it immediately", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()

        expect(unreachable.value).toBe(false)
    })

    it("it flips to true only after UNREACHABLE_DELAY elapses", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()

        vi.advanceTimersByTime(UNREACHABLE_DELAY - 1)
        expect(unreachable.value).toBe(false)

        vi.advanceTimersByTime(1)
        expect(unreachable.value).toBe(true)
    })

    it("markServerReachable before the delay cancels the pending flip entirely", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY - 1)

        markServerReachable()

        vi.advanceTimersByTime(UNREACHABLE_DELAY)
        expect(unreachable.value).toBe(false)
    })

    it("calling markServerUnreachable twice does not schedule two timers", () => {
        const setTimeoutSpy = vi.spyOn(globalThis, "setTimeout")

        markServerUnreachable()
        markServerUnreachable()

        expect(setTimeoutSpy).toHaveBeenCalledTimes(1)
        expect(vi.getTimerCount()).toBe(1)
    })

    it("markServerReachable after it has flipped resets it to false", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY)
        expect(unreachable.value).toBe(true)

        markServerReachable()

        expect(unreachable.value).toBe(false)
    })
})
