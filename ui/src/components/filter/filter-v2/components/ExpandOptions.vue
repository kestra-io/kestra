<template>
    <div
        v-if="showOptions && buttons?.tableOptions?.shown !== false"
        class="table-options-expanded"
    >
        <div class="options-row">
            <div class="options-left">
                <div v-if="tableOptions?.chart?.shown !== false" class="option-item">
                    <span class="option-label">Show Chart</span>
                    <KSSwitch v-model="localChartVisible" showIcon />
                </div>
            </div>

            <div class="options-right">
                <div class="option-item">
                    <KSSwitch showIcon />
                    <span class="option-label">Periodic Refresh</span>
                </div>

                <el-button
                    v-if="tableOptions?.refresh?.shown"
                    @click="handleRefreshData"
                    :icon="Refresh"
                    :size="'default'"
                    class="refresh-button"
                >
                    Refresh Data
                </el-button>

                <el-popover
                    v-if="tableOptions?.columns?.shown !== false"
                    v-model:visible="isColumnsPanelVisible"
                    placement="bottom-end"
                    :width="328"
                    trigger="click"
                    :showArrow="false"
                    @hide="closeColumnsPanel"
                >
                    <template #reference>
                        <el-button size="default" class="columns-button" :icon="CogOutline">
                            Columns
                        </el-button>
                    </template>

                    <div class="customize-columns-panel">
                        <div class="header">
                            <div class="title">
                                <h6>Customize Table Columns</h6>
                                <small>Drag to reorder</small>
                            </div>
                            <el-button type="text" :icon="Close" @click="closeColumnsPanel" size="small" class="close-icon" />
                        </div>

                        <div class="list">
                            <div
                                v-for="column in props.properties?.columns"
                                :key="column.prop"
                                class="column-item"
                            >
                                <div class="column-info">
                                    <Drag class="drag-handle" />
                                    <span class="column-label">
                                        {{ column.label }}
                                    </span>
                                </div>

                                <el-button
                                    type="text"
                                    size="default"
                                    :icon="isVisible(column) ? Eye : EyeOff"
                                    :class="isVisible(column) ? 'selected' : 'unselected'"
                                    @click="handleToggle(column)"
                                />
                            </div>
                        </div>

                        <div class="footer">
                            <small>{{ visibleCount }} of {{ totalCount }} columns visible</small>
                        </div>
                    </div>
                </el-popover>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, computed} from "vue";
    import {FilterProperties, TableOptions, FilterButtons} from "../utils/types.ts";
    import type {ColumnConfig} from "../composable/useTableColumns.ts";
    import {useTableColumns} from "../composable/useTableColumns.ts";
    import {Eye, EyeOff} from "../../utils/icons.ts";
    import Refresh from "vue-material-design-icons/Refresh.vue";
    import CogOutline from "vue-material-design-icons/CogOutline.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import Drag from "vue-material-design-icons/Drag.vue";
    import KSSwitch from "./KSSwitch.vue";

    interface Props {
        showOptions: boolean;
        buttons?: FilterButtons;
        tableOptions?: TableOptions;
        chartVisible?: boolean;
        properties?: FilterProperties;
    }

    const props = defineProps<Props>();

    const emits = defineEmits<{
        "update-chart": [value: boolean];
        "refresh-data": [];
        "update-properties": [columns: string[]];
    }>();

    const localChartVisible = ref(props.chartVisible ?? false);

    watch(() => props.chartVisible, (newVal) => {
        localChartVisible.value = newVal ?? false;
    });

    watch(localChartVisible, (newVal) => {
        emits("update-chart", newVal);
    });

    const isColumnsPanelVisible = ref(false);

    const {
        visibleColumns: localVisibleColumns,
        isVisible,
        toggleColumn
    } = useTableColumns({
        columns: props.properties?.columns ?? [],
        storageKey: props.properties?.storageKey || "",
        initialVisibleColumns: props.properties?.displayColumns ?? []
    });

    const visibleCount = computed(() => localVisibleColumns.value.length);
    const totalCount = computed(() => props.properties?.columns?.length ?? 0);

    const handleRefreshData = () => {
        emits("refresh-data");
    };

    const handleToggle = (column: ColumnConfig) => {
        toggleColumn(column);
        emits("update-properties", localVisibleColumns.value);
    };

    const closeColumnsPanel = () => {
        isColumnsPanelVisible.value = false;
    };
</script>

<style lang="scss" scoped>
.table-options-expanded {
    animation: slideDown 0.2s ease-out;
    border-top: 1px solid var(--ks-border-primary);
    padding: 1rem;

    .options-row {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .options-left {
            display: flex;
            align-items: center;

            .option-item {
                display: flex;
                align-items: center;
                margin-right: 1rem;

                .option-label {
                    font-weight: 500;
                    font-size: 0.875rem;
                    margin: 0 6px;
                }
            }
        }

        .options-right {
            display: flex;
            align-items: center;
            gap: 1rem;

            .option-item {
                display: flex;
                align-items: center;

                .option-label {
                    font-weight: 500;
                    font-size: 0.75rem;
                    margin: 0 0.5rem 0 6px;
                }
            }

            .refresh-button,
            .columns-button {
                background-color: transparent;
                border: none;
                box-shadow: none;
                margin: 0;
                padding: 0;
                font-size: 14px;
            }
        }
    }

    .columns-panel {
        .columns-group-title {
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
            color: var(--bs-secondary);
        }

        .column-item {
            transition: all 0.2s ease;

            &:hover {
                background-color: var(--bs-gray-100);
            }

            .column-name {
                font-size: 0.875rem;
            }
        }

        .columns-container {
            max-height: 300px;
            overflow-y: auto;
        }
    }
}

.customize-columns-panel {
    height: fit-content;
    max-height: 327px;
    display: flex;
    flex-direction: column;
    border-radius: 8px;

    :deep(.el-popper) {
        padding: 0;
    }

    :deep(.el-popover.el-popper) {
        padding: 0;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 12px 16px 8px;
        border-bottom: 1px solid var(--ks-border-primary);
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 1;

        .title {
            h6 {
                margin: 0;
                font-size: 14px;
                font-weight: 700;
            }

            small {
                font-size: 12px;
                color: var(--ks-content-tertiary);
            }
        }

        :deep(.close-icon) {
            color: var(--ks-content-tertiary);
            font-size: 16px;
            cursor: pointer;
            padding-right: 0;
        }
    }

    .list {
        flex: 1;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &:hover {
            scrollbar-color: var(--ks-border-secondary) transparent;
        }
    }

    .footer {
        border-top: 1px solid var(--ks-border-primary);
        flex-shrink: 0;
        position: sticky;
        bottom: 0;
        z-index: 1;
        padding: 8px 16px;
        text-align: center;
    }
}

.column-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 16px;
    transition: all 0.2s ease;
    border-bottom: 1px solid var(--ks-border-primary);

    &:last-child {
        border-bottom: none;
    }

    .column-info {
        display: flex;
        align-items: center;

        .drag-handle {
            margin-right: 0.5rem;
        }
    }
}

@keyframes slideDown {
    from {
        opacity: 0;
        transform: translateY(-10px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

:deep(.column-label) {
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
}

:deep(.el-button.selected) {
    color: var(--ks-chart-success);
}

:deep(.el-button.unselected) {
    color: var(--ks-content-tertiary);

    &:hover {
        color: var(--ks-content-secondary);
    }
}
</style>