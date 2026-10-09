import {describe, it, expect} from "vitest"
import {PendingEventsBuffer} from "./pendingEvents"

describe("PendingEventsBuffer", () => {
    it("prunes by age and respects max size", () => {
        const buffer = new PendingEventsBuffer<string, Record<string, unknown>>({
            maxItems: 2,
            maxAgeMs: 1000,
        })

        buffer.enqueue("a", {}, 0)
        buffer.enqueue("b", {}, 100)
        buffer.enqueue("c", {}, 200)

        expect(buffer.length).toBe(2)

        const drained = buffer.drain(1200)
        expect(drained.map((item) => item.data)).toEqual(["c"])
        expect(buffer.length).toBe(0)
    })

    it("clears items", () => {
        const buffer = new PendingEventsBuffer<number, Record<string, unknown>>({
            maxItems: 3,
            maxAgeMs: 1000,
        })

        buffer.enqueue(1, {}, 0)
        buffer.enqueue(2, {}, 10)
        buffer.clear()

        expect(buffer.length).toBe(0)
    })
})
