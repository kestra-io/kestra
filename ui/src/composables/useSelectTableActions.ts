import {ref, computed, Ref} from "vue"

export function useSelectTableActions({
        dataTableRef,
        selectionMapper
    }: {
        dataTableRef: Ref<any>
        selectionMapper?: (element: any) => any
    }) {
    const queryBulkAction = ref(false)
    const selection = ref<any[]>([])

    const elTable = computed(() => dataTableRef.value?.$refs?.table)

    selectionMapper = selectionMapper ?? ((element: any) => element)

    const handleSelectionChange = (value: any[]) => {
        selection.value = value.map(selectionMapper)

        if (queryBulkAction.value && elTable?.value && value?.length < elTable.value?.data?.length) {
            queryBulkAction.value = false
        }
    }

    const toggleAllUnselected = () => {
        elTable.value?.clearSelection()
        queryBulkAction.value = false
    }

    const toggleAllSelection = () => {
        if (elTable.value?.getSelectionRows().length < elTable.value?.data?.length) {
            elTable.value?.toggleAllSelection()
        }
        queryBulkAction.value = true
    }

    return {
        queryBulkAction,
        selection,
        handleSelectionChange,
        toggleAllUnselected,
        toggleAllSelection,
        selectionMapper
    }
}
