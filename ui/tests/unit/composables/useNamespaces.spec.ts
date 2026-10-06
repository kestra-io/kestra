import {describe, it, expect, vi, beforeEach} from "vitest"
import {storageKeys} from "../../../src/utils/constants"

const searchMock = vi.fn()

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({
        search: searchMock,
    }),
}))

import useNamespaces, {NamespaceIterator, defaultNamespace} from "../../../src/composables/useNamespaces"

describe("useNamespaces", () => {
    it("returns an iterator configured with that fetch size", () => {
        const iterator = useNamespaces(25)

        expect(iterator).toBeInstanceOf(NamespaceIterator)
        expect(iterator.fetchSize).toBe(25)
    })

    it("forwards the options to the iterator", () => {
        const iterator = useNamespaces(25, {existing: true})

        expect(iterator.options).toEqual({existing: true})
    })
})

describe("NamespaceIterator", () => {
    beforeEach(() => {
        searchMock.mockReset()
    })

    describe("fetchCall", () => {
        it("passes the iterator's fetch options to the namespaces store", async () => {
            searchMock.mockResolvedValue({results: [], total: 0})
            const iterator = new NamespaceIterator(10)

            await iterator.fetchCall()

            expect(searchMock).toHaveBeenCalledTimes(1)
            expect(searchMock).toHaveBeenCalledWith({
                commit: false,
                sort: "id:asc",
                page: 1,
                size: 10,
            })
        })

        it("passes extra options to the store call", async () => {
            searchMock.mockResolvedValue({results: [], total: 0})
            const iterator = new NamespaceIterator(15, {existing: true})

            await iterator.fetchCall()

            expect(searchMock).toHaveBeenCalledTimes(1)
            expect(searchMock).toHaveBeenCalledWith({
                commit: false,
                sort: "id:asc",
                page: 1,
                size: 15,
                existing: true,
            })
        })

        it("coerces a response with no total to total: 0", async () => {
            searchMock.mockResolvedValue({results: [{id: "company.team"}]})
            const iterator = new NamespaceIterator(10)

            const result = await iterator.fetchCall()

            expect(result).toEqual({
                results: [{id: "company.team"}],
                total: 0,
            })
        })

        it("passes through a response with a real total", async () => {
            searchMock.mockResolvedValue({results: [{id: "company.team"}], total: 42})
            const iterator = new NamespaceIterator(10)

            const result = await iterator.fetchCall()

            expect(result).toEqual({
                results: [{id: "company.team"}],
                total: 42,
            })
        })
    })
})

describe("defaultNamespace", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    it("returns the stored value", () => {
        localStorage.setItem(storageKeys.DEFAULT_NAMESPACE, "company.team")

        expect(defaultNamespace()).toBe("company.team")
    })

    it("returns null when nothing is stored", () => {
        expect(defaultNamespace()).toBeNull()
    })
})
