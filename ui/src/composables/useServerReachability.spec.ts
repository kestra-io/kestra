import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {isReadonly} from "vue"
import {
    markServerReachable,
    markServerUnreachable,
    UNREACHABLE_DELAY,
    useServerReachability,
} from "./useServerReachability"

describe("useServerReachability", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        markServerReachable()
    })

    afterEach(() => {
        markServerReachable()
        vi.useRealTimers()
    })

    it("starts with unreachable as false", () => {
        const {unreachable} = useServerReachability()
        expect(unreachable.value).toBe(false)
    })

    it("does not flip unreachable immediately when markServerUnreachable is called", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()
        expect(unreachable.value).toBe(false)
    })

    it("flips unreachable to true only after UNREACHABLE_DELAY elapses", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()

        vi.advanceTimersByTime(UNREACHABLE_DELAY - 1)
        expect(unreachable.value).toBe(false)

        vi.advanceTimersByTime(1)
        expect(unreachable.value).toBe(true)
    })

    it("cancels the pending flip entirely when markServerReachable is called before the delay elapses", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY / 2)

        markServerReachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY)

        expect(unreachable.value).toBe(false)
    })

    it("does not schedule two timers when calling markServerUnreachable twice", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()
        vi.advanceTimersByTime(1_000)

        // Calling again while the first timer is pending should be a no-op due to ??=
        markServerUnreachable()

        // After remaining 1,000ms (total 2,000ms), it should flip
        vi.advanceTimersByTime(1_000)
        expect(unreachable.value).toBe(true)
    })

    it("resets unreachable to false when markServerReachable is called after it has flipped", () => {
        const {unreachable} = useServerReachability()

        markServerUnreachable()
        vi.advanceTimersByTime(UNREACHABLE_DELAY)
        expect(unreachable.value).toBe(true)

        markServerReachable()
        expect(unreachable.value).toBe(false)
    })

    it("ensures the ref returned by useServerReachability is readonly", () => {
        const {unreachable} = useServerReachability()
        expect(isReadonly(unreachable)).toBe(true)
    })
})
