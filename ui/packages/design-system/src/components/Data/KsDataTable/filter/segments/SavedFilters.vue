<template>
    <div class="saved-filters-panel">
        <div class="panel-header">
            <h6>
                {{ $t("filter.saved filters") }}
            </h6>
            <KsButton
                link
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
                    <KsTooltip :content="$t('filter.edit filter')" placement="top">
                        <KsButton
                            link
                            size="small"
                            class="edit-button"
                            :icon="PencilOutline"
                            @click.stop="$emit('edit', savedFilter)"
                        />
                    </KsTooltip>
                    <KsTooltip :content="$t('filter.delete filter')" placement="top">
                        <KsButton
                            link
                            size="small"
                            class="delete-button"
                            :icon="Delete"
                            @click.stop="deleteFilter(savedFilter)"
                        />
                    </KsTooltip>
                </div>
            </div>
            <KsAlert v-if="savedFilters.length === 0" type="info" :closable="false">
                {{ $t("filter.empty") }}
                <template #icon>
                    <InformationOutline />
                </template>
            </KsAlert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import {KsMessageBox} from "../../../../Feedback/KsMessageBox"
    import type {SavedFilter} from "../utils/filterTypes"
    import {Close, Delete, InformationOutline, PencilOutline} from "../utils/icons"

    const {t} = useI18n({useScope: "global"})

    defineProps<{
        savedFilters: SavedFilter[];
    }>()

    const emit = defineEmits<{
        close: [];
        load: [savedFilter: SavedFilter];
        edit: [savedFilter: SavedFilter];
        delete: [savedFilter: SavedFilter];
    }>()

    const deleteFilter = (savedFilter: SavedFilter) => {
        KsMessageBox.confirm(t("filter.delete filter confirm"), t("confirmation"), {
            type: "warning",
            confirmButtonText: t("ok"),
            cancelButtonText: t("close"),
        }).then(() => {
            emit("delete", savedFilter)
        }).catch(() => {})
    }
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
        border-bottom: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        top: 0;

        h6 {
            font-size: var(--ks-font-size-sm);
            font-weight: 700;
            margin-bottom: 0.25rem;
        }

        :deep(.kel-button) {
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-base);
            cursor: pointer;

            &:hover {
                color: var(--ks-text-link);
            }
        }
    }

    .saved-filters-list {
        flex: 1;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &:hover {
            scrollbar-color: var(--ks-border-subtle) transparent;
        }

        .saved-filter-item {
            display: flex;
            justify-content: space-between;
            align-items: baseline;
            padding: 0.375rem 1rem;
            cursor: pointer;
            transition: all 0.2s ease;
            border-bottom: 1px solid var(--ks-border-default);

            &:last-child {
                border-bottom: none;
            }

            .saved-filter-name {
                display: block;
                font-size: var(--ks-font-size-sm);
                font-weight: 400;
                margin-bottom: -0.375rem;
            }

            .saved-filter-description {
                font-size: 0.625rem;
                color: var(--ks-text-dim);
            }

            .action-buttons {
                display: flex;
                gap: 0.5rem;

                :deep(.kel-button) {
                    color: var(--ks-text-dim);
                    margin: 0;
                    padding: 0;

                    .play-icon {
                        color: var(--ks-status-success);
                        font-size: var(--ks-font-size-base);
                    }
                }

                :deep(.edit-button:hover) {
                    color: var(--ks-status-running);
                }

                :deep(.delete-button:hover) {
                    color: var(--ks-text-error);
                }
            }
        }

        :deep(.kel-alert) {
            text-align: center;
            color: var(--ks-text-dim);
            padding: 0.875rem;
        }
    }

    :deep(.kel-alert__icon) {
        color: var(--ks-text-info);
        font-size: var(--ks-font-size-xl);
    }
}
</style>
