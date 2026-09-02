import {describe, test, expect, afterEach, beforeEach} from "vitest"
import {defineComponent, nextTick} from "vue"
import {mount} from "@vue/test-utils"

import {useTableColumns, type ColumnConfig} from "../../../src/composables/useTableColumns"

const COLUMNS: ColumnConfig[] = [
    {label: "A", prop: "a", default: true},
    {label: "B", prop: "b", default: true},
    {label: "C", prop: "c", default: false},
]

/**
 * The composable calls `onMounted`, so it needs a real component instance; mounting
 * synchronously runs that hook, so `initializeVisibleColumns` has already applied by the
 * time this returns.
 */
const setup = (storageKey: string, initialVisibleColumns: string[] = []) => {
    let table!: ReturnType<typeof useTableColumns>
    mount(defineComponent({
        setup() {
            table = useTableColumns({columns: COLUMNS, storageKey, initialVisibleColumns})
            return () => null
        },
    }))
    return table
}

describe("useTableColumns", () => {
    beforeEach(() => localStorage.clear())
    afterEach(() => localStorage.clear())

    test("should keep every column hidden when the stored selection is empty", () => {
        localStorage.setItem("columns_all-hidden", "")

        const table = setup("all-hidden")

        expect(table.visibleColumns.value).toEqual([])
        expect(table.visibleCount.value).toBe(0)
    })

    test("should keep every column hidden after re-initializing a deselect-all", () => {
        const table = setup("deselect-all")

        // Toggle only what is on: `c` is not default-flagged, so toggling it would turn it on.
        COLUMNS.filter(column => table.isVisible(column)).forEach(column => table.toggleColumn(column))
        expect(localStorage.getItem("columns_deselect-all")).toBe("")

        table.initializeVisibleColumns()

        expect(table.visibleColumns.value).toEqual([])
    })

    test("should keep the stored columns that still exist and drop the rest", () => {
        localStorage.setItem("columns_partly-stale", "gone,a,c")

        const table = setup("partly-stale")

        expect(table.visibleColumns.value).toEqual(["a", "c"])
        expect(table.visibleCount.value).toBe(2)
        expect(table.totalCount.value).toBe(COLUMNS.length)
    })

    // No initialVisibleColumns, so this exercises the `default` flag branch rather than
    // echoing the argument back.
    test("should fall back to the default-flagged columns when no selection was ever stored", () => {
        const table = setup("never-stored")

        expect(table.visibleColumns.value).toEqual(["a", "b"])
    })

    test("should fall back to the default-flagged columns when the stored columns no longer exist", () => {
        localStorage.setItem("columns_stale", "gone,removed")

        const table = setup("stale")

        expect(table.visibleColumns.value).toEqual(["a", "b"])
    })

    test("should prefer explicit initialVisibleColumns over the default flags", () => {
        const table = setup("explicit", ["c"])

        expect(table.visibleColumns.value).toEqual(["c"])
    })

    test("should not persist a column order until the user reorders one", async () => {
        const storedOrders = () => Object.keys(localStorage).filter(key => key.startsWith("ks-column-order"))

        const table = setup("untouched")

        expect(storedOrders()).toEqual([])

        table.toggleColumn(COLUMNS[2])
        await nextTick()

        expect(storedOrders()).toEqual([])

        table.reorderColumns(0, 2)
        await nextTick()

        expect(storedOrders()).toHaveLength(1)
        expect(localStorage.getItem(storedOrders()[0])).toBe(JSON.stringify(["b", "c", "a"]))
    })
})
