<template>
    <div class="saved-filters-panel">
        <div class="panel-header">
            <h6>
                {{ $t("filter.saved filters") }}
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
                    <el-tooltip :content="$t('filter.edit filter')" placement="top" effect="light">
                        <el-button
                            type="text"
                            size="small"
                            class="edit-button"
                            :icon="PencilOutline"
                            @click.stop="$emit('edit', savedFilter)"
                        />
                    </el-tooltip>
                    <el-tooltip :content="$t('filter.delete filter')" placement="top" effect="light">
                        <el-button
                            type="text"
                            size="small"
                            class="delete-button"
                            :icon="Delete"
                            @click.stop="$emit('delete', savedFilter)"
                        />
                    </el-tooltip>
                </div>
            </div>
            <el-alert v-if="savedFilters.length === 0" type="info" showIcon :closable="false">
                {{ $t("filter.empty") }}
                <template #icon>
                    <InformationOutline />
                </template>
            </el-alert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {SavedFilter} from "../utils/filterTypes";
    import {Close, Delete, InformationOutline, PencilOutline} from "../utils/icons";

    defineProps<{
        savedFilters: SavedFilter[];
    }>();

    defineEmits<{
        close: [];
        load: [savedFilter: SavedFilter];
        edit: [savedFilter: SavedFilter];
        delete: [savedFilter: SavedFilter];
    }>();
</script>

<style lang="scss" scoped>
.saved-filters-panel {
    height: fit-content;
    max-height: 327px;
    display: flex;
    flex-direction: column;
    border-radius: 0.5rem;

    .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 0.75rem 0.75rem 0.5rem 0.75rem;
        border-bottom: 1px solid var(--ks-border-primary);
        flex-shrink: 0;
        position: sticky;
        top: 0;

        h6 {
            font-size: 0.875rem;
            font-weight: 700;
            margin-bottom: 0.25rem;
        }

        :deep(.el-button) {
            color: var(--ks-content-tertiary);
            font-size: 1rem;
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

        .saved-filter-item {
            display: flex;
            justify-content: space-between;
            align-items: baseline;
            padding: 0.375rem 1rem;
            cursor: pointer;
            transition: all 0.2s ease;
            border-bottom: 1px solid var(--ks-border-primary);

            &:last-child {
                border-bottom: none;
            }

            .saved-filter-name {
                display: block;
                font-size: 0.875rem;
                font-weight: 400;
                margin-bottom: -0.375rem;
            }

            .saved-filter-description {
                font-size: 0.625rem;
                color: var(--ks-content-tertiary);
            }

            .action-buttons {
                display: flex;
                gap: 0.5rem;

                :deep(.el-button) {
                    color: var(--ks-content-tertiary);
                    margin: 0;
                    padding: 0;

                    .play-icon {
                        color: var(--ks-chart-success);
                        font-size: 1rem;
                    }
                }

                :deep(.edit-button:hover) {
                    color: var(--ks-content-running);
                }

                :deep(.delete-button:hover) {
                    color: var(--ks-content-alert);
                }
            }
        }

        :deep(.el-alert) {
            text-align: center;
            color: var(--ks-content-tertiary);
            padding: 0.875rem;
        }
    }

    :deep(.el-alert__icon) {
        color: var(--ks-content-info);
        font-size: 1.5rem;
    }
}
</style>