<template>
    <div class="filters-panel">
        <div class="header">
            <div class="title">
                <h6>{{ $t("filter.customize") }}</h6>
                <small>{{ $t("filter.select filter") }}</small>
            </div>
            <KsButton
                link
                :icon="Close"
                @click="$emit('close')"
                size="small"
                class="close-icon"
            />
        </div>

        <div class="list">
            <div
                v-for="key in groupableKeys"
                :key="key.key"
                class="item"
                @click="addFilterForKey(key)"
            >
                <div class="info">
                    <span class="label">{{ key.label }}</span>
                    <small>{{ key.description }}</small>
                </div>

                <KsButton
                    link
                    size="default"
                    :icon="Plus"
                    class="unselected"
                    @click.stop="addFilterForKey(key)"
                />
            </div>
        </div>

        <div class="advanced" @click="$emit('open-advanced')">
            <span class="label">{{ $t("filter.add_advanced_filter") }}</span>
            <KsButton
                link
                size="default"
                :icon="Plus"
                class="advanced-add"
                @click.stop="$emit('open-advanced')"
            />
        </div>

        <div class="footer">
            <small>{{ $t("filter.filters_added", {selected: selectedCount, total: totalCount}) }}</small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {Close, Plus} from "../utils/icons"
    import {
        type FilterConfiguration,
        type FilterKeyConfig,
        type AppliedFilter,
    } from "../utils/filterTypes"
    import {buildNewFilter} from "../utils/filterChipFactory"

    const props = defineProps<{
        configuration: FilterConfiguration;
        appliedFilters: AppliedFilter[];
    }>()

    const emits = defineEmits<{
        close: [];
        "add-filter": [filter: AppliedFilter];
        "remove-filter": [id: string];
        "open-advanced": [];
    }>()

    const groupableKeys = computed(() =>
        props.configuration.keys.filter((key) => key.groupable !== false),
    )

    const selectedCount = computed(() =>
        new Set(props.appliedFilters.map(f => f.key)).size,
    )
    const totalCount = computed(() => groupableKeys.value.length)

    const addFilterForKey = (key: FilterKeyConfig) => {
        const newFilter = buildNewFilter(key)
        if (newFilter) emits("add-filter", newFilter)
    }

</script>

<style lang="scss" scoped>
.filters-panel {
    height: fit-content;
    max-height: 500px;
    display: flex;
    flex-direction: column;
    border-radius: var(--ks-radius-lg);

    small {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-dim);
        font-weight: 400;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: var(--ks-spacing-3) var(--ks-spacing-4) var(--ks-spacing-2);
        background-color: var(--ks-bg-active);
        border-bottom: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 1;

        .title {
            h6 {
                margin: 0;
                font-size: var(--ks-font-size-sm);
                font-weight: 700;
            }
        }

        :deep(.close-icon) {
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-base);
            cursor: pointer;
            padding-right: 0;

            &:hover {
                color: var(--ks-text-link);
            }
        }
    }

    .list {
        flex: 1;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &:hover {
            scrollbar-color: var(--ks-border-subtle) transparent;
        }
    }

    .item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        cursor: pointer;
        transition: all 0.2s ease;
        border-bottom: 1px solid var(--ks-border-default);

        &:hover {
            background-color: var(--ks-bg-hover-elevated);
        }

        &:last-child {
            border-bottom: none;
        }

        .info {
            display: flex;
            align-items: start;
            flex-direction: column;

            .label {
                font-size: var(--ks-font-size-sm);
                font-weight: 400;
                line-height: 1.375rem;
            }
        }
    }

    .advanced {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        cursor: pointer;
        border-top: 1px solid var(--ks-border-default);
        background-color: var(--ks-bg-active);
        transition: background-color 0.2s ease;

        &:hover {
            background-color: var(--ks-bg-hover-elevated);
        }

        .label {
            font-size: var(--ks-font-size-sm);
            font-weight: 600;
            color: var(--ks-content-link, var(--ks-text-link));
        }
    }

    .footer {
        border-top: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        bottom: 0;
        z-index: 1;
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        text-align: center;
    }
}

:deep(.kel-button.advanced-add) {
    color: var(--ks-text-link);
    font-size: var(--ks-font-size-lg);
    pointer-events: auto;
}

:deep(.kel-button.unselected) {
    color: var(--ks-status-success);
    user-select: none;
    pointer-events: auto;
    font-size: var(--ks-font-size-lg);

    &:hover {
        color: var(--ks-text-success);
    }
}
</style>