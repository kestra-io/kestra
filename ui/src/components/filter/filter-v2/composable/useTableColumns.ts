import {ref, computed, watch} from "vue"

export interface ColumnConfig {
    prop: string;
    label: string;
}

export function useTableColumns(options: { columns: ColumnConfig[], storageKey: string, initialVisibleColumns: string[] }) {
    const {columns, storageKey, initialVisibleColumns} = options

    const visibleColumns = ref<string[]>(initialVisibleColumns.length > 0 ? initialVisibleColumns : columns.map(c => c.prop))

    if (storageKey) {
        const stored = localStorage.getItem(storageKey)
        if (stored) {
            try {
                const parsed = JSON.parse(stored)
                if (Array.isArray(parsed)) visibleColumns.value = parsed
            } catch {
                // ignore
            }
        }
    }

        const orderedColumns = computed(() => {
        return visibleColumns.value.map(prop => columns.find(c => c.prop === prop)).filter(Boolean) as ColumnConfig[]
    })

    const isVisible = (column: ColumnConfig) => visibleColumns.value.includes(column.prop)

    const toggleColumn = (column: ColumnConfig) => {
        if (isVisible(column)) {
            visibleColumns.value = visibleColumns.value.filter(p => p !== column.prop)
        } else {
            visibleColumns.value.push(column.prop)
        }
    }

    watch(visibleColumns, (newVal) => {
        if (storageKey) {
            localStorage.setItem(storageKey, JSON.stringify(newVal))
        }
    }, {deep: true})

    return {
        visibleColumns,
        orderedColumns,
        isVisible,
        toggleColumn
    }
}