import {describe, expect, it} from "vitest"
import {createChartLoadQueue} from "./chartLoadQueue"

function deferred<T>() {
    let resolve!: (value: T) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })
    return {promise, resolve, reject}
}

async function flushMicrotasks() {
    // enough ticks for the queue's internal promise chain (thenable adoption included) to settle
    for (let i = 0; i < 10; i++) await Promise.resolve()
}

describe("createChartLoadQueue", () => {
    it("should run tasks immediately while under the concurrency limit", async () => {
        const queue = createChartLoadQueue(2)
        let started = 0

        const first = deferred<string>()
        const second = deferred<string>()
        queue.enqueue(() => {
            started++
            return first.promise
        })
        queue.enqueue(() => {
            started++
            return second.promise
        })

        await flushMicrotasks()
        expect(started).toBe(2)
    })

    it("should hold tasks above the limit until a running one settles", async () => {
        const queue = createChartLoadQueue(2)
        const started: number[] = []

        const first = deferred<void>()
        const second = deferred<void>()
        const third = deferred<void>()
        queue.enqueue(() => {
            started.push(1)
            return first.promise
        })
        queue.enqueue(() => {
            started.push(2)
            return second.promise
        })
        queue.enqueue(() => {
            started.push(3)
            return third.promise
        })

        await flushMicrotasks()
        expect(started).toEqual([1, 2])

        first.resolve()
        await flushMicrotasks()
        expect(started).toEqual([1, 2, 3])
    })

    it("should drain queued tasks in FIFO order", async () => {
        const queue = createChartLoadQueue(1)
        const order: number[] = []

        const gates = [deferred<void>(), deferred<void>(), deferred<void>()]
        gates.forEach((gate, index) => {
            queue.enqueue(() => {
                order.push(index)
                return gate.promise
            })
        })

        await flushMicrotasks()
        gates[0].resolve()
        await flushMicrotasks()
        gates[1].resolve()
        await flushMicrotasks()
        gates[2].resolve()
        await flushMicrotasks()

        expect(order).toEqual([0, 1, 2])
    })

    it("should resolve with the task result and reject with the task error", async () => {
        const queue = createChartLoadQueue(1)

        await expect(queue.enqueue(() => Promise.resolve("value"))).resolves.toBe("value")
        await expect(queue.enqueue(() => Promise.reject(new Error("boom")))).rejects.toThrow("boom")
    })

    it("should release the slot when a task fails so queued tasks still run", async () => {
        const queue = createChartLoadQueue(1)
        let ranAfterFailure = false

        const failing = queue.enqueue(() => Promise.reject(new Error("boom")))
        const following = queue.enqueue(() => {
            ranAfterFailure = true
            return Promise.resolve()
        })

        await expect(failing).rejects.toThrow("boom")
        await following
        expect(ranAfterFailure).toBe(true)
    })

    it("should release the slot when a task throws synchronously", async () => {
        const queue = createChartLoadQueue(1)

        const throwing = queue.enqueue(() => {
            throw new Error("sync boom")
        })
        const following = queue.enqueue(() => Promise.resolve("ok"))

        await expect(throwing).rejects.toThrow("sync boom")
        await expect(following).resolves.toBe("ok")
    })
})
