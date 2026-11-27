import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"

import {
    useServerReachability,
    markServerUnreachable,
    markServerReachable,
    UNREACHABLE_DELAY,
    PROBE_INTERVAL,
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

    it("clears itself once the server answers a probe, with no other request needed, and keeps waiting through a gateway error", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch")
            .mockRejectedValueOnce(new TypeError("Failed to fetch"))
            .mockResolvedValueOnce(new Response("", {status: 503}))
            .mockResolvedValueOnce(new Response("pong", {status: 200}))
        const {unreachable} = useServerReachability()

        markServerUnreachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY)
        expect(fetchSpy).not.toHaveBeenCalled()

        await vi.advanceTimersByTimeAsync(PROBE_INTERVAL * 2)
        expect(unreachable.value).toBe(true)

        await vi.advanceTimersByTimeAsync(PROBE_INTERVAL)
        expect(unreachable.value).toBe(false)
        expect(fetchSpy).toHaveBeenCalledTimes(3)
        expect(String(fetchSpy.mock.calls[0][0])).toMatch(/\/ping$/)

        await vi.advanceTimersByTimeAsync(PROBE_INTERVAL * 3)
        expect(fetchSpy).toHaveBeenCalledTimes(3)
    })

    it("probes at once when the browser reports it is back online", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("pong", {status: 200}))
        const {unreachable} = useServerReachability()
        markServerUnreachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY)

        window.dispatchEvent(new Event("online"))
        await vi.waitFor(() => expect(unreachable.value).toBe(false))

        expect(fetchSpy).toHaveBeenCalledOnce()
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
