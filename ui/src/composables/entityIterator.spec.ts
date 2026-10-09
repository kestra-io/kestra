import {describe, it, expect} from "vitest"

import {EntityIterator, type FetchResult} from "./entityIterator"

class TestEntityIterator<T> extends EntityIterator<T> {
    callCount = 0
    private readonly items: T[]
    private readonly totalCount: number

    constructor(items: T[], fetchSize: number, options?: Record<string, unknown>, totalCount?: number) {
        super(fetchSize, options)
        this.items = items
        this.totalCount = totalCount ?? items.length
    }

    async fetchCall(): Promise<FetchResult<T>> {
        this.callCount++
        const opts = this.fetchOptions()
        const start = (opts.page - 1) * opts.size
        return {
            total: this.totalCount,
            results: this.items.slice(start, start + opts.size),
        }
    }
}

describe("EntityIterator", () => {
    it("the constructor throws when fetchSize is 0 or negative", () => {
        expect(() => new TestEntityIterator([], 0)).toThrow("fetchSize must be greater than 0")
        expect(() => new TestEntityIterator([], -1)).toThrow("fetchSize must be greater than 0")
        expect(() => new TestEntityIterator([], -10)).toThrow("fetchSize must be greater than 0")
    })

    it("fetchOptions increments the page each call and carries size, sort: 'id:asc' and commit: false", () => {
        const iterator = new TestEntityIterator([], 10)

        expect(iterator.fetchOptions()).toEqual({
            commit: false,
            sort: "id:asc",
            page: 1,
            size: 10,
        })
        expect(iterator.fetchOptions()).toEqual({
            commit: false,
            sort: "id:asc",
            page: 2,
            size: 10,
        })
    })

    it("extra options passed to the constructor are merged into fetchOptions", () => {
        const iterator = new TestEntityIterator([], 5, {namespace: "company.team", custom: 123})

        expect(iterator.fetchOptions()).toEqual({
            commit: false,
            sort: "id:asc",
            page: 1,
            size: 5,
            namespace: "company.team",
            custom: 123,
        })
    })

    it("next() returns the fetched batch and records the total", async () => {
        const iterator = new TestEntityIterator(["a", "b", "c", "d", "e"], 2)

        expect(iterator.total).toBeUndefined()

        const batch = await iterator.next()

        expect(batch).toEqual(["a", "b"])
        expect(iterator.total).toBe(5)
        expect(iterator.callCount).toBe(1)
    })

    it("next() returns an empty array once everything has been fetched, without calling fetchCall again", async () => {
        const iterator = new TestEntityIterator(["a", "b"], 2)

        const batch1 = await iterator.next()
        expect(batch1).toEqual(["a", "b"])
        expect(iterator.callCount).toBe(1)

        const batch2 = await iterator.next()
        expect(batch2).toEqual([])
        expect(iterator.callCount).toBe(1)
    })

    it("single() returns entities one at a time, fetching a new batch only when the buffer runs dry", async () => {
        const iterator = new TestEntityIterator(["a", "b", "c"], 2)

        expect(iterator.callCount).toBe(0)

        expect(await iterator.single()).toBe("a")
        expect(iterator.callCount).toBe(1)

        expect(await iterator.single()).toBe("b")
        expect(iterator.callCount).toBe(1)

        expect(await iterator.single()).toBe("c")
        expect(iterator.callCount).toBe(2)
    })

    it("single() returns undefined once the source is exhausted", async () => {
        const iterator = new TestEntityIterator(["a"], 1)

        expect(await iterator.single()).toBe("a")
        expect(await iterator.single()).toBeUndefined()
        expect(await iterator.single()).toBeUndefined()
        expect(iterator.callCount).toBe(1)
    })

    it("all() fetches every page for a total that is an exact multiple of fetchSize, and for one that is not", async () => {
        const exactIterator = new TestEntityIterator(["a", "b", "c", "d"], 2)
        const exactResults = await exactIterator.all()
        expect(exactResults).toEqual(["a", "b", "c", "d"])
        expect(exactIterator.callCount).toBe(2)
        expect(exactIterator.total).toBe(4)

        const inexactIterator = new TestEntityIterator(["a", "b", "c", "d", "e"], 2)
        const inexactResults = await inexactIterator.all()
        expect(inexactResults).toEqual(["a", "b", "c", "d", "e"])
        expect(inexactIterator.callCount).toBe(3)
        expect(inexactIterator.total).toBe(5)
    })

    it("all() called twice does not refetch", async () => {
        const iterator = new TestEntityIterator(["a", "b", "c"], 2)

        const first = await iterator.all()
        expect(first).toEqual(["a", "b", "c"])
        expect(iterator.callCount).toBe(2)

        const second = await iterator.all()
        expect(second).toEqual(["a", "b", "c"])
        expect(iterator.callCount).toBe(2)
    })
})
