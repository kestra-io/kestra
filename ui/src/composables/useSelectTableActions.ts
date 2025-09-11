import {computed, Ref, ref} from "vue";

export function useSelectTableActions<IN = any, OUT = any>(options: {
    dataTableRef: Ref<any | undefined>,
    selectionMapper: (element: IN) => OUT
}) {
    const dataTableRef = computed<{
        clearSelection: () => void;
        getSelectionRows: () => any[];
        data: any[];
        toggleAllSelection: () => void;
    } | undefined>(() => options.dataTableRef.value?.$refs.table);
    const queryBulkAction = ref(false);
    const selection = ref<OUT[]>([]);

    const handleSelectionChange = (value: IN[]) => {
        selection.value = value.map(a => options.selectionMapper(a));
    };

    const toggleAllUnselected = () => {
        dataTableRef.value?.clearSelection();
        queryBulkAction.value = false;
    };

    const toggleAllSelection = () => {
        const selectionRowsLength = dataTableRef.value?.getSelectionRows().length ?? 0;
        if (selectionRowsLength < (dataTableRef.value?.data.length ?? -1)) {
            dataTableRef.value?.toggleAllSelection();
        }
        queryBulkAction.value = true;
    };

    return {
        queryBulkAction,
        selection,
        handleSelectionChange,
        toggleAllUnselected,
        toggleAllSelection,
    };
}