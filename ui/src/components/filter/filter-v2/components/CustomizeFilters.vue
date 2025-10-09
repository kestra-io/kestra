<template>
    <div class="customize-filters-panel">
        <div class="header">
            <div class="title">
                <h6>Customize Filters</h6>
                <small>Drag to reorder</small>
            </div>
            <el-button type="text" :icon="Close" @click="$emit('close')" size="small" class="close-icon" />
        </div>

        <div class="list">
            <div
                v-for="key in orderedKeys"
                :key="key.key"
                class="item"
            >
                <div class="info">
                    <Drag class="handle" />
                    <span class="label">{{ key.label }}</span>
                </div>

                <el-button
                    type="text"
                    size="default"
                    :icon="isSelected(key) ? Eye : EyeOff"
                    :class="isSelected(key) ? 'selected' : 'unselected'"
                    @click="toggleFilter(key)"
                />
            </div>
        </div>

        <div class="footer">
            <small>{{ selectedCount }} of {{ totalCount }} filters visible</small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import {Eye, EyeOff} from "../../utils/icons";
    import {
        FilterConfiguration,
        FilterKeyConfig,
        AppliedFilter
    } from "../utils/types";
    import Close from "vue-material-design-icons/Close.vue";
    import Drag from "vue-material-design-icons/Drag.vue";

    const props = defineProps<{
        configuration: FilterConfiguration;
        appliedFilters: AppliedFilter[];
    }>();
    
    const emits = defineEmits<{
        "add-filter": [filter: AppliedFilter];
        "remove-filter": [id: string];
        close: [];
    }>();

    const orderedKeys = computed(() =>
        keyOrder.value
            .map(keyId => props.configuration.keys.find(key => key.key === keyId))
            .filter(Boolean) as FilterKeyConfig[]
    );

    const selectedCount = computed(() => selectedKeys.value.length);
    const totalCount = computed(() => props.configuration.keys.length);

    const keyOrder = ref(props.configuration.keys.map(key => key.key));

    const isSelected = (key: FilterKeyConfig): boolean =>
        selectedKeys.value.includes(key.key);

    const selectedKeys = ref<string[]>(props.appliedFilters.map(f => f.key));

    const toggleFilter = (key: FilterKeyConfig) => {
        const index = selectedKeys.value.indexOf(key.key);
        if (index > -1) {
            selectedKeys.value.splice(index, 1);
            const filterToRemove = props.appliedFilters.find(f => f.key === key.key);
            if (filterToRemove) {
                emits("remove-filter", filterToRemove.id);
            }
        } else {
            selectedKeys.value.push(key.key);
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
    .customize-filters-panel {
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
            padding: 8px 16px;
            transition: all 0.2s ease;
            border-bottom: 1px solid var(--ks-border-primary);
            cursor: move;

            &:last-child {
                border-bottom: none;
            }

            .info {
                display: flex;
                align-items: center;

                .handle {
                    color: var(--ks-content-tertiary);
                    margin-right: 8px;
                    cursor: grab;

                    &:active {
                        cursor: grabbing;
                    }
                }

                .label {
                    font-size: 14px;
                    font-weight: 400;
                    line-height: 22px;
                }
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

            small {
                color: var(--ks-content-tertiary);
                font-size: 12px;
                font-weight: 400;
            }
        }
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