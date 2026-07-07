import {describe, test, expect, beforeEach} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import {useTableColumns, type ColumnConfig} from "../../../src/components/Data/KsDataTable/filter/composables/useTableColumns"

const COLUMNS: ColumnConfig[] = [
    {label: "A", prop: "a", default: true},
    {label: "B", prop: "b", default: true},
    {label: "C", prop: "c", default: true},
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
