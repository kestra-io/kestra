<template>
    <div class="list-preview-container">
        <el-table :data="previewData" stripe class="ion-table-preview">
            <el-table-column type="index" :index="indexMethod" label="#" width="60" align="center" />
            <el-table-column v-for="(column, index) in generateTableColumns" :key="index" :prop="column" :label="column">
                <template #default="scope">
                    <div :class="['cell-wrapper', {'expanded': expandedCells.has(getCellKey(scope.$index, column))}]">
                        <span v-if="isComplex(scope.row[column])">
                            <span class="preview-cell">{{ getTruncatedContent(scope.row[column], scope.$index, column) }}</span>
                            <el-button
                                v-if="needsExpansion(scope.row[column])"
                                link
                                type="primary"
                                size="small"
                                class="expand-button"
                                @click="toggleExpand(scope.$index, column)"
                            >
                                {{ expandedCells.has(getCellKey(scope.$index, column)) ? $t('preview.collapse') : $t('preview.expand') }}
                            </el-button>
                        </span>
                        <span v-else class="preview-cell">
                            {{ scope.row[column] }}
                        </span>
                    </div>
                </template>
            </el-table-column>
        </el-table>

        <Pagination
            v-if="totalPages > 1"
            :total="props.value.length"
            :size="pageSize"
            :page="currentPage"
            @page-changed="onPageChanged"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import Pagination from "./layout/Pagination.vue";

    const MAX_CELL_CHARS = 2000;

    const props = defineProps({
        value: {
            type: Array as () => Record<string, any>[],
            required: true
        }
    });

    const expandedCells = ref(new Set<string>());
    const currentPage = ref(1);
    const pageSize = ref(50);

    const previewData = computed(() => {
        const startIndex = (currentPage.value - 1) * pageSize.value;
        const endIndex = startIndex + pageSize.value;
        return props.value.slice(startIndex, endIndex);
    });

    const totalPages = computed(() => {
        return Math.ceil(props.value.length / pageSize.value);
    });

    const indexMethod = (index: number) => {
        return (currentPage.value - 1) * pageSize.value + index + 1;
    };

    const onPageChanged = (payload: { page?: number; size?: number }): void => {
        if (payload.page !== undefined) {
            currentPage.value = payload.page;
        }
        if (payload.size !== undefined) {
            pageSize.value = payload.size;
        }
        expandedCells.value.clear();
    };

    const generateTableColumns = computed(() => {
        const allKeys = new Set<string>();
        props.value.forEach(item => {
            if (item && typeof item === "object") {
                Object.keys(item).forEach(key => allKeys.add(key));
            }
        });
        return Array.from(allKeys);
    });


    const isComplex = (data: any): boolean => {
        return data !== null && typeof data === "object";
    };

    const getCellKey = (rowIndex: number, column: string): string => {
        return `${rowIndex}-${column}`;
    };

    const needsExpansion = (data: any): boolean => {
        const stringified = JSON.stringify(data, null, 2);
        return stringified.length > MAX_CELL_CHARS;
    };

    const getTruncatedContent = (data: any, rowIndex: number, column: string): string => {
        const cellKey = getCellKey(rowIndex, column);
        const stringified = JSON.stringify(data, null, 2);

        if (expandedCells.value.has(cellKey)) {
            return stringified;
        }

        if (stringified.length > MAX_CELL_CHARS) {
            return stringified.slice(0, MAX_CELL_CHARS) + "... [truncated]";
        }

        return stringified;
    };

    const toggleExpand = (rowIndex: number, column: string): void => {
        const cellKey = getCellKey(rowIndex, column);
        if (expandedCells.value.has(cellKey)) {
            expandedCells.value.delete(cellKey);
        } else {
            expandedCells.value.add(cellKey);
        }
    };
</script>

<style scoped lang="scss">
    .list-preview-container {
        width: 100%;
        overflow: auto;
    }

    .ion-table-preview {
        table-layout: fixed;
        width: 100%;

        :deep(.el-table__body-wrapper) {
            overflow-x: auto;
        }
    }

    .cell-wrapper {
        max-height: 120px;
        overflow: hidden;
        position: relative;
        display: block;
        word-break: break-word;

        &.expanded {
            max-height: none;
            overflow: visible;
        }
    }

    .preview-cell {
        display: block;
        white-space: pre-wrap;
        word-wrap: break-word;
        font-family: monospace;
        font-size: 12px;
        line-height: 1.4;
    }

    .expand-button {
        margin-top: 4px;
        font-size: 12px;
    }

    :deep(.ks-editor) {
        .editor-container {
            box-shadow: none;
            background-color: transparent !important;
            padding: 0;

            .monaco-editor, .monaco-editor-background {
                background-color: transparent;
            }
        }
    }
</style>