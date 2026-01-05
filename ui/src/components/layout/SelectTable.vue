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
            <el-table-column type="selection" v-if="selectable && showSelection" reserveSelection />
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

    const toggleRowExpansion = (row: any, expand?: boolean) => {
        table.value?.toggleRowExpansion(row, expand);
    };

    const selectionChanged = (selection: any[]) => {
        hasSelection.value = selection.length > 0;
        emit("selection-change", selection);
    };

    const clearSelection = () => {
        table.value?.clearSelection();
        hasSelection.value = false;
    };

    const setSelection = (selection: any[]) => {
        table.value?.clearSelection();
        if (Array.isArray(selection)) {
            const isFunction = typeof props.rowKey === "function";
            selection.forEach(sel => {
                const row = props.data.find(r => isFunction
                    ? props.rowKey(r) === props.rowKey(sel)
                    : r[props.rowKey] === sel[props.rowKey]);
                if (row) table.value?.toggleRowSelection(row, true);
            });
        }
        selectionChanged(selection);
    };

    const computeHeaderSize = () => {
        const tableElement = table.value?.$el;
        if (!tableElement || !container.value) return;
        container.value.style.setProperty("--table-header-width", `${tableElement.clientWidth}px`);
        container.value.style.setProperty("--table-header-height", `${tableElement.querySelector("thead").clientHeight}px`);
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
            table.value?.clearSelection();
        } else {
            const currentSelection = table.value?.getSelectionRows() ?? [];
            const validSelection = currentSelection.filter((sel: any) => {
                const isFunction = typeof props.rowKey === "function";
                return props.data.some(r => isFunction
                    ? props.rowKey(r) === props.rowKey(sel)
                    : r[props.rowKey] === sel[props.rowKey]);
            });
            if (validSelection.length !== currentSelection.length) {
                table.value?.clearSelection();
                hasSelection.value = false;
            } else if (table.value) {
                selectionChanged(currentSelection);
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
