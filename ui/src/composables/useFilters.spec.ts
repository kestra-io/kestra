import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {useFilters, compare} from "./useFilters"

describe("useFilters", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("returns an empty array when nothing is stored", () => {
        const {getSavedItems} = useFilters("executions")
        expect(getSavedItems()).toEqual([])
    })

    it("does not throw when localStorage contains malformed JSON", () => {
        localStorage.setItem("saved__executions", "{ corrupted json }")
        const {getSavedItems} = useFilters("executions")

        expect(() => {
            const items = getSavedItems()
            expect(items).toEqual([])
        }).not.toThrow()
    })

    it("does not throw when localStorage contains non-array JSON value", () => {
        localStorage.setItem("saved__executions", JSON.stringify({some: "object"}))
        const {getSavedItems} = useFilters("executions")

        expect(() => {
            const items = getSavedItems()
            expect(items).toEqual([])
        }).not.toThrow()
    })

    it("retrieves saved items with string values", () => {
        const stored = [
            {name: "Production", value: "namespace:company.prod state:FAILED"},
            {name: "Staging", value: "namespace:company.staging"},
        ]
        localStorage.setItem("saved__flows", JSON.stringify(stored))

        const {getSavedItems} = useFilters("flows")
        expect(getSavedItems()).toEqual(stored)
    })

    it("migrates legacy filter objects and updates localStorage", () => {
        const legacyStored = [
            {
                name: "Legacy Filter",
                value: [
                    {field: "namespace", operation: "EQUALS", value: "prod"},
                    {field: "state", operation: "EQUALS", value: "FAILED"},
                ],
            },
        ]
        localStorage.setItem("saved__executions", JSON.stringify(legacyStored))

        const {getSavedItems} = useFilters("executions")
        const items = getSavedItems()

        expect(items).toEqual([
            {name: "Legacy Filter", value: "namespace[EQUALS]prod state[EQUALS]FAILED"},
        ])

        // Verify it was migrated in localStorage
        const reloaded = JSON.parse(localStorage.getItem("saved__executions") || "[]")
        expect(reloaded).toEqual([
            {name: "Legacy Filter", value: "namespace[EQUALS]prod state[EQUALS]FAILED"},
        ])
    })

    it("saves items to localStorage with the correct prefix key", () => {
        const {setSavedItems, getSavedItems} = useFilters("dashboards")
        const newItems = [{name: "Daily", value: "timeRange:PT24H"}]

        setSavedItems(newItems)

        expect(JSON.parse(localStorage.getItem("saved__dashboards") || "[]")).toEqual(newItems)
        expect(getSavedItems()).toEqual(newItems)
    })

    it("removes saved item correctly", () => {
        const {setSavedItems, removeSavedItem, getSavedItems} = useFilters("executions")
        const item1 = {name: "Item 1", value: "val1"}
        const item2 = {name: "Item 2", value: "val2"}

        setSavedItems([item1, item2])
        expect(getSavedItems()).toHaveLength(2)

        removeSavedItem(item1)
        expect(getSavedItems()).toEqual([item2])
    })

    it("compares items correctly", () => {
        expect(compare({a: 1}, {a: 1})).toBe(false)
        expect(compare({a: 1}, {a: 2})).toBe(true)
    })
})
