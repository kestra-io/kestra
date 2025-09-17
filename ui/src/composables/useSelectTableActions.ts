import {ref, computed} from "vue"

export function useSelectTableActions(selectTableRef: any) {
    const queryBulkAction = ref(false)
    const selection = ref<any[]>([])

    const elTable = computed(() => selectTableRef.value?.$refs?.table)

    const handleSelectionChange = (value: any[]) => {
        selection.value = value.map(selectionMapper)
    }

    const toggleAllUnselected = () => {
        elTable.value.clearSelection()
        queryBulkAction.value = false
    }

    const toggleAllSelection = () => {
        if (elTable.value.getSelectionRows().length < elTable.value.data.length) {
            elTable.value.toggleAllSelection()
        }
        queryBulkAction.value = true
    }

    const selectionMapper = (element: any) => element

    return {
        queryBulkAction,
        selection,
        handleSelectionChange,
        toggleAllUnselected,
        toggleAllSelection,
        selectionMapper
    }
}
