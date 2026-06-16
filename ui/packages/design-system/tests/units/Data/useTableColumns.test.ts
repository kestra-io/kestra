import {describe, test, expect, beforeEach} from "vitest"
import {useTableColumns, type ColumnConfig} from "../../../src/components/Data/KsDataTable/filter/composables/useTableColumns"

const COLUMNS: ColumnConfig[] = [
    {label: "A", prop: "a", default: true},
    {label: "B", prop: "b", default: true},
    {label: "C", prop: "c", default: true},
]

const setup = (storageKey: string, initialVisibleColumns = ["a", "b", "c"]) => {
    const table = useTableColumns({columns: COLUMNS, storageKey, initialVisibleColumns})
    table.initializeVisibleColumns()
    return table
}

describe("useTableColumns reorder", () => {
    beforeEach(() => localStorage.clear())

    test("setColumnOrder applies the full order and persists it", () => {
        const table = setup("set-order")

        table.setColumnOrder(["c", "a", "b"])

        expect(table.orderedColumns.value.map(c => c.prop)).toEqual(["c", "a", "b"])
        expect(table.visibleColumns.value).toEqual(["c", "a", "b"])
        expect(localStorage.getItem("columns_set-order")).toBe("c,a,b")
    })

    test("setColumnOrder keeps hidden columns hidden while reordering visible ones", () => {
        const table = setup("set-order-hidden")
        table.updateVisibleColumns(["a", "c"])

        table.setColumnOrder(["c", "b", "a"])

        expect(table.orderedColumns.value.map(c => c.prop)).toEqual(["c", "b", "a"])
        expect(table.visibleColumns.value).toEqual(["c", "a"])
        expect(localStorage.getItem("columns_set-order-hidden")).toBe("c,a")
    })
})
