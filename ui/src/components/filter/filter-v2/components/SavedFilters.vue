<template>
    <div class="saved-filters-panel">
        <div class="panel-header">
            <h6>
                Saved Filters
            </h6>
            <el-button
                type="text"
                :icon="Close"
                @click="$emit('close')"
                size="small"
            />
        </div>

        <div class="saved-filters-list">
            <div
                v-for="savedFilter in savedFilters"
                :key="savedFilter.id"
                class="saved-filter-item"
                @click="$emit('load', savedFilter)"
            >
                <div class="saved-filter-info">
                    <span class="saved-filter-name">{{ savedFilter.name }}</span>
                    <small v-if="savedFilter.description" class="saved-filter-description">
                        {{ savedFilter.description }}
                    </small>
                </div>
                <div class="action-buttons">
                    <el-tooltip content="Apply filter" placement="top">
                        <el-button
                            type="text"
                            size="small"
                            :icon="Play"
                            @click.stop="$emit('load', savedFilter)"
                        />
                    </el-tooltip>
                    <el-tooltip content="Edit filter" placement="top">
                        <el-button
                            type="text"
                            size="small"
                            :icon="Pencil"
                            @click.stop="$emit('edit', savedFilter)"
                        />
                    </el-tooltip>
                    <el-tooltip content="Delete filter" placement="top">
                        <el-button
                            type="text"
                            size="small"
                            :icon="Delete"
                            @click.stop="$emit('delete', savedFilter)"
                        />
                    </el-tooltip>
                </div>
            </div>
            <el-alert v-if="savedFilters.length === 0" type="info" showIcon :closable="false">
                No saved filters available.
            </el-alert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {SavedFilter} from "../utils/types";
    import Close from "vue-material-design-icons/Close.vue";
    import Play from "vue-material-design-icons/Play.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Delete from "vue-material-design-icons/Delete.vue";

    interface Props {
        savedFilters: SavedFilter[];
    }

    defineProps<Props>();

    defineEmits<{
        load: [savedFilter: SavedFilter];
        edit: [savedFilter: SavedFilter];
        delete: [savedFilter: SavedFilter];
        close: [];
    }>();
</script>

<style lang="scss" scoped>
    .saved-filters-panel {
        height: fit-content;
        max-height: 327px;
        display: flex;
        flex-direction: column;
        border-radius: 8px;

        .panel-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            padding: 12px 12px 8px 12px;
            border-bottom: 1px solid var(--ks-border-primary);
            flex-shrink: 0;
            position: sticky;
            top: 0;

            h6 {
                font-size: 14px;
                font-weight: 700;
                margin-bottom: 0.25rem;
            }

            :deep(.el-button) {
                color: var(--ks-content-tertiary);
                font-size: 16px;
                cursor: pointer;

                &:hover {
                    color: var(--ks-content-link);
                }
            }
        }

        .saved-filters-list {
            flex: 1;
            overflow-y: auto;
            scrollbar-width: thin;
            scrollbar-color: transparent transparent;

            &:hover {
                scrollbar-color: var(--ks-border-secondary) transparent;
            }

            &::-webkit-scrollbar {
                width: 6px;
            }

            &::-webkit-scrollbar-track {
                background: transparent;
            }

            &::-webkit-scrollbar-thumb {
                background: transparent;
                border-radius: 3px;
                transition: background-color 0.2s ease;
            }

            &:hover::-webkit-scrollbar-thumb {
                background: var(--ks-border-secondary);
            }

            &::-webkit-scrollbar-thumb:hover {
                background: var(--ks-border-primary);
            }

            .saved-filter-item {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 4px 16px;
                cursor: pointer;
                transition: all 0.2s ease;
                border-bottom: 1px solid var(--ks-border-primary);

                &:last-child {
                    border-bottom: none;
                }

                .saved-filter-name {
                    display: block;
                    font-size: 14px;
                    font-weight: 400;
                    margin-bottom: -6px;
                }

                .saved-filter-description {
                    font-size: 10px;
                    color: var(--ks-content-tertiary);
                }

                .action-buttons {
                    display: flex;
                    gap: 8px;

                    :deep(.el-button) {
                        color: var(--ks-content-tertiary);
                        margin: 0;
                        padding: 0;

                        &:hover {
                            color: var(--ks-content-secondary);
                        }

                        .play-icon {
                            color: var(--ks-chart-success);
                            font-size: 16px;
                        }
                    }
                }
            }

            :deep(.el-alert) {
                text-align: center;
                color: var(--ks-content-tertiary);
            }
        }

        :deep(.el-alert__icon) {
            color: var(--ks-content-info);
            font-size: 1.5rem;
        }
    }
</style>