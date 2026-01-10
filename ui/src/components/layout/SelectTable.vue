<template>
    <div ref="container" class="position-relative">
        <div v-if="hasSelection && data.length" class="bulk-select-header">
            <slot name="select-actions" />
        </div>

        <el-table
            ref="table"
            v-bind="$attrs"
            :data
            :rowKey
            :emptyText="data.length === 0 ? noDataText : ''"
            @selection-change="selectionChanged"
        >
            <el-table-column 
                type="selection" 
                v-if="selectable && showSelection" 
                reserveSelection
            >
                <template #default="scope">
                    <el-checkbox 
                        :modelValue="isRowSelected(scope.row)"
                        @click="handleCheckboxClick(scope.row, scope.$index, $event)"
                    />
                </template>
            </el-table-column>
            <slot name="default" />
        </el-table>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted, onUnmounted, onUpdated, watch, nextTick} from "vue";

    const props = withDefaults(defineProps<{
        showSelection?: boolean;
        selectable?: boolean;
        expandable?: boolean;
        data?: any[];
        noDataText?: string;
        rowKey?: string | ((row: any) => string | number);
    }>(), {
        showSelection: true,
        selectable: true,
        expandable: false,
        data: () => [],
        noDataText: undefined,
        rowKey: "id"
    });

    const emit = defineEmits<{
        "selection-change": [selection: any[]];
    }>();

    const table = ref<any>(null);
    const hasSelection = ref(false);
    const container = ref<HTMLElement>(null);
    const lastSelectedIndex = ref<number | null>(null);
    const lastActionWasSelect = ref<boolean>(true);
    const currentSelection = ref<any[]>([]);

    const getRowKey = (row: any) => {
        return typeof props.rowKey === "function" ? props.rowKey(row) : row[props.rowKey];
    };

    const isRowSelected = (row: any): boolean => {
        const rowKey = getRowKey(row);
        return currentSelection.value.some(sel => getRowKey(sel) === rowKey);
    };

    const handleCheckboxClick = (row: any, rowIndex: number, event: MouseEvent) => {
        if (event.shiftKey && lastSelectedIndex.value !== null && lastSelectedIndex.value !== rowIndex) {
            event.preventDefault();
            event.stopPropagation();
            const start = Math.min(lastSelectedIndex.value, rowIndex);
            const end = Math.max(lastSelectedIndex.value, rowIndex);
         
            const clickedRowSelected = isRowSelected(row);
            const shouldSelect =  !clickedRowSelected;
            
            for (let i = start; i <= end; i++) {
                const targetRow = props.data[i];
                if (targetRow) {
                    table.value?.toggleRowSelection(targetRow, shouldSelect);
                }
            }
        } else {
            const willBeSelected = !isRowSelected(row);
            table.value?.toggleRowSelection(row, willBeSelected);
            lastSelectedIndex.value = rowIndex;
            lastActionWasSelect.value = willBeSelected;
        }
    };

    const toggleRowExpansion = (row: any, expand?: boolean) => {
        table.value?.toggleRowExpansion(row, expand);
    };

    const selectionChanged = (selection: any[]) => {
        currentSelection.value = selection;
        hasSelection.value = selection.length > 0;
        emit("selection-change", selection);
    };

    const clearSelection = () => {
        table.value?.clearSelection();
        hasSelection.value = false;
        lastSelectedIndex.value = null;
    };

    const setSelection = (selection: any[]) => {
        table.value?.clearSelection();
        if (Array.isArray(selection)) {
            selection.forEach(sel => {
                const selKey = getRowKey(sel);
                const row = props.data.find(r => getRowKey(r) === selKey);
                if (row) table.value?.toggleRowSelection(row, true);
            });
        }
        selectionChanged(selection);
    };

    const computeHeaderSize = () => {
        const tableElement = table.value?.$el;
        if (!tableElement || !container.value) return;
        container.value.style.setProperty("--table-header-width", `${tableElement.clientWidth}px`);
        const thead = tableElement.querySelector("thead");
        if (thead) {
            container.value.style.setProperty("--table-header-height", `${thead.clientHeight}px`);
        }
    };

    onMounted(() => {
        window.addEventListener("resize", computeHeaderSize);
    });

    onUnmounted(() => {
        window.removeEventListener("resize", computeHeaderSize);
    });

    onUpdated(() => {
        computeHeaderSize();
    });

    watch(() => props.data, () => {
        if (props.data.length === 0) {
            hasSelection.value = false;
            lastSelectedIndex.value = null;
            table.value?.clearSelection();
        } else {
            const currentSel = table.value?.getSelectionRows() ?? [];
            const validSelection = currentSel.filter((sel: any) => {
                const selKey = getRowKey(sel);
                return props.data.some(r => getRowKey(r) === selKey);
            });
            if (validSelection.length !== currentSel.length) {
                table.value?.clearSelection();
                hasSelection.value = false;
                lastSelectedIndex.value = null;
            } else if (table.value) {
                selectionChanged(currentSel);
            }
        }
    }, {immediate: true});

    const waitTableRender = () => nextTick();

    defineExpose({
        setSelection,
        clearSelection,
        toggleRowExpansion,
        waitTableRender
    });
</script>
<style scoped lang="scss">
    .bulk-select-header {
        z-index: 1;
        position: absolute;
        height: var(--table-header-height);
        width: var(--table-header-width);
        background-color: var(--ks-background-table-header);
        border-radius: var(--bs-border-radius-lg) var(--bs-border-radius-lg) 0 0;
        border-bottom: 1px solid var(--ks-border-primary);
        overflow-x: auto;

        & ~ .el-table {
            z-index: 0;
        }
    }

    @media (max-width: 500px) {
        :deep(.el-table__empty-text) {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }
</style>