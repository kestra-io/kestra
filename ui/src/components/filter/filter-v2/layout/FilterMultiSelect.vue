<template>
    <div class="filter-multi-select-panel">
        <div class="panel-header">
            <div class="search-section">
                <el-input
                    v-model="searchQuery"
                    placeholder="Search..."
                    size="default"
                    clearable
                    :prefixIcon="Magnify"
                />
            </div>
            <div class="controls-section">
                <div class="check-border">
                    <el-checkbox
                        v-model="allSelected"
                        :indeterminate="isIndeterminate"
                        @change="handleSelectAllChange"
                        size="default"
                    >
                        Select All
                    </el-checkbox>
                </div>
                <div class="check-border">
                    <el-checkbox
                        v-model="noneSelected"
                        @change="handleDeselectAllChange"
                        size="default"
                    >
                        Deselect All
                    </el-checkbox>
                </div>
            </div>
        </div>
        <div class="options-list">
            <div
                v-for="option in filteredOptions"
                :key="option.value"
                class="option-item"
            >
                <div class="option-content">
                    <Status
                        v-if="props.filterKey === 'state'"
                        :status="option.value"
                        size="small"
                    />
                    <span v-else class="option-label">{{ option.label }}</span>
                </div>
                <el-checkbox
                    :modelValue="modelValue.includes(option.value)"
                    @change="(checked: boolean) => handleOptionChange(option.value, checked)"
                    size="default"
                />
            </div>
            <el-alert type="info" showIcon :closable="false" v-if="filteredOptions.length === 0" class="no-options">
                No options found
            </el-alert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import {ref, computed, PropType} from "vue";
    import Status from "../../../../components/Status.vue";

    interface FilterValue {
        value: string;
        label: string;
    }

    const props = defineProps({
        modelValue: {type: Array as PropType<string[]>, required: true},
        options: {type: Array as PropType<FilterValue[]>, required: true},
        searchable: {type: Boolean, default: undefined},
        placeholder: {type: String, default: undefined},
        label: {type: String, default: undefined},
        filterKey: {type: String, default: undefined}
    });

    const emits = defineEmits(["update:modelValue", "apply", "reset"]);

    const searchQuery = ref("");

    const filteredOptions = computed(() =>
        !searchQuery.value.trim()
            ? props.options
            : props.options.filter(option =>
                option.label.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
                option.value.toLowerCase().includes(searchQuery.value.toLowerCase())
            )
    );

    const allSelected = computed(() =>
        filteredOptions.value.length > 0 &&
        filteredOptions.value.every(option => props.modelValue.includes(option.value))
    );

    const isIndeterminate = computed(() => {
        if (filteredOptions.value.length === 0) return false;
        const selectedCount = filteredOptions.value.filter(option =>
            props.modelValue.includes(option.value)
        ).length;
        return selectedCount > 0 && selectedCount < filteredOptions.value.length;
    });

    const noneSelected = computed(() =>
        filteredOptions.value.length === 0 ||
        filteredOptions.value.every(option => !props.modelValue.includes(option.value))
    );

    const handleSelectAllChange = (checked: boolean) => {
        const currentValues = new Set(props.modelValue);
        filteredOptions.value.forEach(option =>
            checked ? currentValues.add(option.value) : currentValues.delete(option.value)
        );
        emits("update:modelValue", Array.from(currentValues));
    };

    const handleDeselectAllChange = (checked: boolean) => {
        if (checked) {
            const currentValues = new Set(props.modelValue);
            filteredOptions.value.forEach(option => currentValues.delete(option.value));
            emits("update:modelValue", Array.from(currentValues));
        }
    };

    const handleOptionChange = (value: string, checked: boolean) =>
        emits("update:modelValue",
              checked
                  ? [...props.modelValue, value]
                  : props.modelValue.filter(v => v !== value)
        );
</script>

<style lang="scss" scoped>
    .filter-multi-select-panel {
        height: fit-content;
        max-height: 300px;
        display: flex;
        flex-direction: column;

        .panel-header {
            border-bottom: 1px solid var(--ks-border-primary);
            flex-shrink: 0;
            position: sticky;
            top: 0;
            z-index: 1;
            background-color: var(--ks-surface-primary);

            .search-section {
                padding: 0.75rem;
                padding-bottom: 0.5rem;
            }

            .controls-section {
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0.25rem 1rem;
                margin-bottom: 8px;

                .check-border {
                    border: 1px solid var(--ks-border-primary);
                    border-radius: 4px;
                    padding: 0 12px;

                    :deep(.el-checkbox__label) {
                        font-size: 12px;
                        color: var(--ks-content-primary);
                    }
                }
            }
        }

        .options-list {
            flex: 1;
            overflow-y: auto;
            scrollbar-width: thin;
            scrollbar-color: transparent transparent;

            &:hover {
                scrollbar-color: var(--ks-border-secondary) transparent;
            }

            .option-item {
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0 0.75rem;
                transition: all 0.2s ease;
                border-bottom: 1px solid var(--ks-border-secondary);

                &:last-child {
                    border-bottom: none;
                }

                .option-content {
                    display: flex;
                    align-items: center;

                    .option-label {
                        font-size: 14px;
                        font-weight: 400;
                    }
                }
            }

            .no-options {
                text-align: center;
                color: var(--ks-content-tertiary);
                font-size: 14px;

                :deep(.el-alert__icon) {
                    color: var(--ks-content-info);
                    font-size: 1.5rem;
                }
            }
        }

        :deep(.el-input__inner) {
            font-size: 14px;

            &::placeholder {
                color: var(--ks-content-tertiary);
            }
        }
    }
</style>