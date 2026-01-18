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

        <div v-if="totalPages > 1" class="pagination-controls">
            <el-pagination
                v-model:currentPage="currentPage"
                :pageSize="MAX_PREVIEW_ROWS"
                :total="props.value.length"
                layout="prev, pager, next"
                :hideOnSinglePage="true"
                @current-change="goToPage"
            />
            <div class="pagination-summary">
                {{ $t('preview.showing_rows', {start: startRow, end: endRow, total: props.value.length}) }}
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    const MAX_PREVIEW_ROWS = 50; 
    const MAX_CELL_CHARS = 2000; 

    const props = defineProps({
        value: {
            type: Array as () => Record<string, any>[],
            required: true
        }
    });

    const expandedCells = ref(new Set<string>());
    const currentPage = ref(1);

    const previewData = computed(() => {
        const startIndex = (currentPage.value - 1) * MAX_PREVIEW_ROWS;
        const endIndex = startIndex + MAX_PREVIEW_ROWS;
        return props.value.slice(startIndex, endIndex);
    });

    const totalPages = computed(() => {
        const pages = Math.ceil(props.value.length / MAX_PREVIEW_ROWS);
        return pages;
    });

    const indexMethod = (index: number) => {
        return (currentPage.value - 1) * MAX_PREVIEW_ROWS + index + 1;
    };


    const startRow = computed(() => {
        return (currentPage.value - 1) * MAX_PREVIEW_ROWS + 1;
    });

    const endRow = computed(() => {
        return Math.min(currentPage.value * MAX_PREVIEW_ROWS, props.value.length);
    });

    const goToPage = (page: number): void => {
        if (page >= 1 && page <= totalPages.value) {
            currentPage.value = page;
            expandedCells.value.clear();
        }
    };

    const generateTableColumns = computed(() => {
        const allKeys = new Set<string>();
        previewData.value.forEach(item => {
            Object.keys(item).forEach(key => allKeys.add(key));
        });
        return Array.from(allKeys);
    });

    const isComplex = (data: any): boolean => {
        return data instanceof Array || data instanceof Object;
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

    .pagination-controls {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        padding: 16px;
        border-top: 1px solid var(--ks-border-primary);
        background-color: var(--ks-background-secondary);
    }

    .pagination-summary {
        font-size: 14px;
        color: var(--ks-text-secondary);
        text-align: center;
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