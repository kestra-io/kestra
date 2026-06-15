<template>
    <div class="saved-filters-panel">
        <div v-if="savedFilters.length" class="saved-filters-list">
            <div
                v-for="savedFilter in savedFilters"
                :key="savedFilter.id"
                class="saved-filter-item"
                @click="$emit('load', savedFilter)"
            >
                <div class="saved-filter-info">
                    <div class="saved-filter-title">
                        <KsIcon class="bookmark-icon">
                            <BookmarkOutline />
                        </KsIcon>
                        <span class="saved-filter-name">{{ savedFilter.name }}</span>
                    </div>
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
        </div>

        <div v-else class="saved-filters-empty">
            <KsIcon class="empty-icon">
                <BookmarkOffOutline />
            </KsIcon>
            <span class="empty-title">{{ $t("filter.empty title") }}</span>
            <small class="empty-subtitle">{{ $t("filter.empty subtitle") }}</small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import {KsMessageBox} from "../../../../Feedback/KsMessageBox"
    import type {SavedFilter} from "../utils/filterTypes"
    import {BookmarkOffOutline, BookmarkOutline, Delete, PencilOutline} from "../utils/icons"

    const {t} = useI18n({useScope: "global"})

    defineProps<{
        savedFilters: SavedFilter[];
    }>()

    const emit = defineEmits<{
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
    max-height: 327px;
    display: flex;
    flex-direction: column;

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
            align-items: flex-start;
            gap: var(--ks-spacing-2);
            margin: var(--ks-spacing-1) var(--ks-spacing-2);
            padding: var(--ks-spacing-2) var(--ks-spacing-3);
            cursor: pointer;
            border-radius: var(--ks-radius-base);
            transition: background-color 0.2s ease;

            &:hover {
                background-color: var(--ks-bg-hover);
            }

            .bookmark-icon {
                flex-shrink: 0;
                color: var(--ks-icon-active);
                font-size: var(--ks-font-size-base);
            }

            .saved-filter-info {
                flex: 1;
                min-width: 0;

                .saved-filter-title {
                    display: flex;
                    align-items: center;
                    gap: var(--ks-spacing-2);
                }

                .saved-filter-name {
                    font-size: var(--ks-font-size-sm);
                    font-weight: 600;
                    color: var(--ks-text-primary);
                }

                .saved-filter-description {
                    display: block;
                    font-size: var(--ks-font-size-xs);
                    color: var(--ks-text-dim);
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }
            }

            .action-buttons {
                display: flex;
                align-self: center;
                gap: var(--ks-spacing-2);
                flex-shrink: 0;
                opacity: 0;
                transition: opacity 0.2s ease;

                :deep(.kel-button) {
                    color: var(--ks-text-dim);
                    margin: 0;
                    padding: 0;
                }

                :deep(.edit-button:hover) {
                    color: var(--ks-status-running);
                }

                :deep(.delete-button:hover) {
                    color: var(--ks-text-error);
                }
            }

            &:hover .action-buttons,
            &:focus-within .action-buttons {
                opacity: 1;
            }
        }
    }

    .saved-filters-empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-6) var(--ks-spacing-4);

        .empty-icon {
            margin-bottom: var(--ks-spacing-1);
            color: var(--ks-text-dim);
            font-size: 2rem;
        }

        .empty-title {
            font-size: var(--ks-font-size-sm);
            font-weight: 600;
            color: var(--ks-text-primary);
        }

        .empty-subtitle {
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-dim);
        }
    }
}
</style>
