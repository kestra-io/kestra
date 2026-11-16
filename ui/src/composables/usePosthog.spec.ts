import {describe, expect, it, vi} from "vitest"
import type {CaptureResult} from "posthog-js"
import {dropResizeObserverNoise} from "./usePosthog"

vi.mock("../stores/api", () => ({useApiStore: () => ({})}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({})}))

describe("dropResizeObserverNoise", () => {
    const exception = (value: string): CaptureResult => ({
        uuid: "event-uuid",
        event: "$exception",
        properties: {$exception_list: [{type: "Error", value}]},
    })

    it("should drop the ResizeObserver loop exception", () => {
        expect(dropResizeObserverNoise(exception("ResizeObserver loop completed with undelivered notifications."))).toBeNull()
    })

    it("should drop the legacy ResizeObserver loop limit message", () => {
        expect(dropResizeObserverNoise(exception("ResizeObserver loop limit exceeded"))).toBeNull()
    })

    it("should drop it when only the exception message property carries the text", () => {
        const event: CaptureResult = {
            uuid: "event-uuid",
            event: "$exception",
            properties: {$exception_message: "ResizeObserver loop completed with undelivered notifications."},
        }

        expect(dropResizeObserverNoise(event)).toBeNull()
    })

    it("should keep any other exception", () => {
        const event = exception("TypeError: x is not a function")

        expect(dropResizeObserverNoise(event)).toBe(event)
    })

    it("should keep non-exception events even if they mention the message", () => {
        const event: CaptureResult = {
            uuid: "event-uuid",
            event: "custom",
            properties: {$exception_message: "ResizeObserver loop completed with undelivered notifications."},
        }

        expect(dropResizeObserverNoise(event)).toBe(event)
    })

    it("should pass a null event through", () => {
        expect(dropResizeObserverNoise(null)).toBeNull()
    })
})
