import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {compare, useFilters} from "./useFilters"

beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
})

afterEach(() => {
    localStorage.clear()
})

describe("compare", () => {
    it("returns false for two deeply equal objects", () => {
        expect(compare({a: 1, b: "x"}, {a: 1, b: "x"})).toBe(false)
    })

    it("returns true when a property value differs", () => {
        expect(compare({a: 1}, {a: 2})).toBe(true)
    })

    it("returns true when one object has an extra key", () => {
        expect(compare({a: 1}, {a: 1, b: 2})).toBe(true)
    })

    it("returns false for two empty objects", () => {
        expect(compare({}, {})).toBe(false)
    })

    it("returns false for nested objects with identical structure", () => {
        expect(compare({a: {b: {c: 3}}}, {a: {b: {c: 3}}})).toBe(false)
    })

    it("returns true for nested objects that differ in a deep value", () => {
        expect(compare({a: {b: 1}}, {a: {b: 2}})).toBe(true)
    })
})

describe("useFilters", () => {
    describe("getSavedItems", () => {
        it("returns an empty array when the storage key is absent", () => {
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([])
        })

        it("throws when storage contains invalid JSON", () => {
            localStorage.setItem("saved__flows", "not-json{{{")
            const {getSavedItems} = useFilters("flows")
            expect(() => getSavedItems()).toThrow()
        })

        it("returns string-valued items unchanged", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "my filter", value: "status=RUNNING"},
            ]))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([{name: "my filter", value: "status=RUNNING"}])
        })

        it("returns multiple string-valued items unchanged", () => {
            const items = [
                {name: "filter A", value: "status=RUNNING"},
                {name: "filter B", value: "namespace=io.kestra"},
            ]
            localStorage.setItem("saved__flows", JSON.stringify(items))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual(items)
        })

        it("converts a single legacy EQUALS filter to '<field>=<value>'", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "status filter", value: [{field: "status", operation: "EQUALS", value: "RUNNING"}]},
            ]))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([{name: "status filter", value: "status=RUNNING"}])
        })

        it("converts a legacy NOT_EQUALS filter to '<field>!=<value>'", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "not running", value: [{field: "status", operation: "NOT_EQUALS", value: "RUNNING"}]},
            ]))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([{name: "not running", value: "status!=RUNNING"}])
        })

        it("converts a legacy GREATER_THAN filter to '<field>><value>'", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "big", value: [{field: "count", operation: "GREATER_THAN", value: "5"}]},
            ]))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([{name: "big", value: "count>5"}])
        })

        it("wraps a legacy value containing spaces in double quotes", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "label filter", value: [{field: "name", operation: "EQUALS", value: "My Flow"}]},
            ]))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([{name: "label filter", value: "name=\"My Flow\""}])
        })

        it("does not quote a legacy value that contains no spaces", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "ns", value: [{field: "namespace", operation: "EQUALS", value: "io.kestra"}]},
            ]))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([{name: "ns", value: "namespace=io.kestra"}])
        })

        it("joins multiple legacy filters with a single space", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {
                    name: "combined",
                    value: [
                        {field: "status", operation: "EQUALS", value: "RUNNING"},
                        {field: "namespace", operation: "EQUALS", value: "io.kestra"},
                    ],
                },
            ]))
            const {getSavedItems} = useFilters("flows")
            expect(getSavedItems()).toEqual([{name: "combined", value: "status=RUNNING namespace=io.kestra"}])
        })

        it("writes the migrated format back to localStorage when a legacy entry is present", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "old", value: [{field: "status", operation: "EQUALS", value: "RUNNING"}]},
            ]))
            const setItemSpy = vi.spyOn(Storage.prototype, "setItem")
            const {getSavedItems} = useFilters("flows")
            getSavedItems()
            expect(setItemSpy).toHaveBeenCalledWith(
                "saved__flows",
                JSON.stringify([{name: "old", value: "status=RUNNING"}]),
            )
        })

        it("does not write back to localStorage when all items are already strings", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "modern", value: "status=RUNNING"},
            ]))
            const setItemSpy = vi.spyOn(Storage.prototype, "setItem")
            const {getSavedItems} = useFilters("flows")
            getSavedItems()
            expect(setItemSpy).not.toHaveBeenCalled()
        })
    })

    describe("setSavedItems", () => {
        it("writes to the correct saved__<prefix> key", () => {
            const {setSavedItems} = useFilters("executions")
            const items = [{name: "my exec filter", value: "status=FAILED"}]
            setSavedItems(items)
            expect(localStorage.getItem("saved__executions")).toBe(JSON.stringify(items))
        })

        it("uses separate storage keys for different prefixes", () => {
            const flows = useFilters("flows")
            const executions = useFilters("executions")
            flows.setSavedItems([{name: "f", value: "status=RUNNING"}])
            executions.setSavedItems([{name: "e", value: "status=FAILED"}])
            expect(localStorage.getItem("saved__flows")).toBe(JSON.stringify([{name: "f", value: "status=RUNNING"}]))
            expect(localStorage.getItem("saved__executions")).toBe(JSON.stringify([{name: "e", value: "status=FAILED"}]))
        })
    })

    describe("removeSavedItem", () => {
        it("removes only the matching item and leaves the rest", () => {
            const items = [
                {name: "filter A", value: "status=RUNNING"},
                {name: "filter B", value: "namespace=io.kestra"},
            ]
            localStorage.setItem("saved__flows", JSON.stringify(items))
            const {removeSavedItem} = useFilters("flows")
            removeSavedItem({name: "filter A", value: "status=RUNNING"})
            expect(JSON.parse(localStorage.getItem("saved__flows") ?? "[]")).toEqual([
                {name: "filter B", value: "namespace=io.kestra"},
            ])
        })

        it("leaves storage unchanged when the item to remove is not present", () => {
            const items = [{name: "filter A", value: "status=RUNNING"}]
            localStorage.setItem("saved__flows", JSON.stringify(items))
            const {removeSavedItem} = useFilters("flows")
            removeSavedItem({name: "filter B", value: "namespace=io.kestra"})
            expect(JSON.parse(localStorage.getItem("saved__flows") ?? "[]")).toEqual(items)
        })

        it("results in an empty array when the only item is removed", () => {
            localStorage.setItem("saved__flows", JSON.stringify([
                {name: "only", value: "status=RUNNING"},
            ]))
            const {removeSavedItem} = useFilters("flows")
            removeSavedItem({name: "only", value: "status=RUNNING"})
            expect(JSON.parse(localStorage.getItem("saved__flows") ?? "null")).toEqual([])
        })
    })
})
