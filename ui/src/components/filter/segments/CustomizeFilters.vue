<template>
    <div class="filters-panel">
        <div class="header">
            <div class="title">
                <h6>{{ $t("filter.customize") }}</h6>
                <small>{{ $t("filter.select filter") }}</small>
            </div>
            <el-button
                link
                :icon="Close"
                @click="$emit('close')"
                size="small"
                class="close-icon"
            />
        </div>

        <div class="list">
            <div
                v-for="key in configuration.keys"
                :key="key.key"
                class="item"
                @click="toggleFilter(key)"
            >
                <div class="info">
                    <span class="label" :class="{'selected': isSelected(key)}">{{ key.label }}</span>
                    <small :class="{'selected': isSelected(key)}">{{ key.description }}</small>
                </div>

                <el-button
                    link
                    size="default"
                    :icon="isSelected(key) ? undefined : Plus"
                    :class="isSelected(key) ? 'selected' : 'unselected'"
                    @click.stop="toggleFilter(key)"
                />
            </div>
        </div>

        <div class="footer">
            <small>{{ $t("filter.filters_added", {selected: selectedCount, total: totalCount}) }}</small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {Close, Plus} from "../utils/icons";
    import {
        FilterConfiguration,
        FilterKeyConfig,
        AppliedFilter
    } from "../utils/filterTypes";

    const props = defineProps<{
        configuration: FilterConfiguration;
        appliedFilters: AppliedFilter[];
    }>();

    const emits = defineEmits<{
        close: [];
        "add-filter": [filter: AppliedFilter];
        "remove-filter": [id: string];
    }>();

    const selectedCount = computed(() => selectedKeys.value.size);
    const totalCount = computed(() => props.configuration.keys.length);

    const isSelected = (key: FilterKeyConfig): boolean =>
        selectedKeys.value.has(key.key);

    const selectedKeys = ref<Set<string>>(new Set(props.appliedFilters.map(f => f.key)));

    watch(() => props.appliedFilters, (newAppliedFilters) => {
        selectedKeys.value = new Set(newAppliedFilters.map(f => f.key));
    }, {deep: true});

    const toggleFilter = (key: FilterKeyConfig) => {
        if (selectedKeys.value.has(key.key)) {
            selectedKeys.value.delete(key.key);
            const filterToRemove = props.appliedFilters.find(f => f.key === key.key);
            if (filterToRemove) {
                emits("remove-filter", filterToRemove.id);
            }
        } else {
            selectedKeys.value.add(key.key);
            const newFilter: AppliedFilter = {
                id: `${key.key}-${Date.now()}`,
                key: key.key,
                keyLabel: key.label,
                comparator: key.comparators?.[0],
                comparatorLabel: key.comparators?.[0],
                value: [],
                valueLabel: ""
            };
            emits("add-filter", newFilter);
        }
    };
</script>

<style lang="scss" scoped>
.filters-panel {
    height: fit-content;
    max-height: 500px;
    display: flex;
    flex-direction: column;
    border-radius: 8px;

    small {
        font-size: 0.75rem;
        color: var(--ks-content-tertiary);
        font-weight: 400;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 0.75rem 1rem 0.5rem;
        background-color: var(--ks-background-table-header);
        border-bottom: 1px solid var(--ks-border-primary);
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 1;

        .title {
            h6 {
                margin: 0;
                font-size: 0.875rem;
                font-weight: 700;
            }
        }

        :deep(.close-icon) {
            color: var(--ks-content-tertiary);
            font-size: 1rem;
            cursor: pointer;
            padding-right: 0;

            &:hover {
                color: var(--ks-content-link);
            }
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

    .item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.5rem 1rem;
        cursor: pointer;
        transition: all 0.2s ease;
        border-bottom: 1px solid var(--ks-border-primary);

        &:hover {
            background-color: var(--ks-dropdown-background-hover);
        }

        &:last-child {
            border-bottom: none;
        }

        .info {
            display: flex;
            align-items: start;
            flex-direction: column;

            .label {
                font-size: 0.875rem;
                font-weight: 400;
                line-height: 1.375rem;

                &.selected {
                    color: var(--ks-content-inactive);
                }
            }

            small {
                &.selected {
                    color: var(--ks-content-inactive);
                }
            }
        }
    }

    .footer {
        border-top: 1px solid var(--ks-border-primary);
        flex-shrink: 0;
        position: sticky;
        bottom: 0;
        z-index: 1;
        padding: 0.5rem 1rem;
        text-align: center;
    }
}

:deep(.el-button.unselected) {
    color: var(--ks-chart-success);
    user-select: none;
    pointer-events: auto;
    font-size: 1.25rem;

    &:hover {
        color: var(--ks-content-success);
    }
}
</style>