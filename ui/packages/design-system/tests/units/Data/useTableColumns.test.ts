import {describe, test, expect, beforeEach} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import {useTableColumns, type ColumnConfig} from "../../../src/components/Data/KsDataTable/filter/composables/useTableColumns"

const COLUMNS: ColumnConfig[] = [
    {label: "A", prop: "a", default: true},
    {label: "B", prop: "b", default: true},
    {label: "C", prop: "c", default: false},
]

// useTableColumns calls onMounted internally, so it needs a real component
// instance to attach to — calling it bare would warn "onMounted is called
// when there is no active component instance". Mounting synchronously runs
// that onMounted hook, so initializeVisibleColumns is already applied by the
// time this returns.
const setup = (storageKey: string, initialVisibleColumns = ["a", "b", "c"]) => {
    let table!: ReturnType<typeof useTableColumns>
    mount(defineComponent({
        setup() {
            table = useTableColumns({columns: COLUMNS, storageKey, initialVisibleColumns})
            return () => null
        },
    }))
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

describe("useTableColumns persistence", () => {
    beforeEach(() => localStorage.clear())

    test("should keep every column hidden when the stored selection is empty", () => {
        localStorage.setItem("columns_all-hidden", "")

        const table = setup("all-hidden")

        expect(table.visibleColumns.value).toEqual([])
        expect(table.visibleCount.value).toBe(0)
    })

    test("should keep every column hidden after re-initializing a deselect-all", () => {
        const table = setup("deselect-all")

        COLUMNS.forEach(column => table.toggleColumn(column))
        expect(localStorage.getItem("columns_deselect-all")).toBe("")

        table.initializeVisibleColumns()

        expect(table.visibleColumns.value).toEqual([])
    })

    // No initialVisibleColumns, so these exercise the `default` flag branch rather than
    // echoing the argument straight back.
    test("should fall back to the default-flagged columns when no selection was ever stored", () => {
        const table = setup("never-stored", [])

        expect(table.visibleColumns.value).toEqual(["a", "b", "c"])
    })

    test("should fall back to the default-flagged columns when the stored columns no longer exist", () => {
        localStorage.setItem("columns_stale", "gone,removed")

        const table = setup("stale", [])

        expect(table.visibleColumns.value).toEqual(["a", "b", "c"])
    })
})
