import {describe, test, expect, afterEach} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {useTableColumns, type ColumnConfig} from "../../../src/composables/useTableColumns"

const STORAGE_KEY = "displayTestColumns"

const COLUMNS: ColumnConfig[] = [
    {label: "Description", prop: "description", default: true},
    {label: "Tags", prop: "tags", default: false},
]

const mountColumns = (columns: ColumnConfig[]) => {
    let result!: ReturnType<typeof useTableColumns>
    mount(defineComponent({
        setup() {
            result = useTableColumns({columns, storageKey: STORAGE_KEY})
            return () => h("div")
        },
    }))
    return result
}

describe("useTableColumns", () => {
    afterEach(() => {
        localStorage.clear()
    })

    test("drops stored props that no longer exist in the offered columns", () => {
        // Given
        localStorage.setItem(`columns_${STORAGE_KEY}`, "namespace,description,tags")

        // When
        const {visibleColumns, visibleCount, totalCount} = mountColumns(COLUMNS)

        // Then
        expect(visibleColumns.value).toEqual(["description", "tags"])
        expect(visibleCount.value).toBeLessThanOrEqual(totalCount.value)
    })

    test("falls back to the default columns when nothing stored matches", () => {
        // Given
        localStorage.setItem(`columns_${STORAGE_KEY}`, "namespace")

        // When
        const {visibleColumns} = mountColumns(COLUMNS)

        // Then
        expect(visibleColumns.value).toEqual(["description"])
    })

    test("uses the default columns when nothing is stored", () => {
        // When
        const {visibleColumns} = mountColumns(COLUMNS)

        // Then
        expect(visibleColumns.value).toEqual(["description"])
    })
})
