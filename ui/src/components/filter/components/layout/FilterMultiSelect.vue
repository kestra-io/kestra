<template>
    <div class="multi-select-panel">
        <div class="panel-header">
            <div v-if="props.searchable" class="search-section">
                <el-input
                    v-model="searchQuery"
                    size="default"
                    clearable
                    :placeholder="$t('filter.search options')"
                    :prefixIcon="Magnify"
                />
            </div>
            <div class="controls-section">
                <div class="check-border">
                    <el-checkbox
                        v-model="allSelected"
                        size="default"
                        :indeterminate="isPartiallySelected"
                        @change="handleSelectAllChange"
                    >
                        {{ $t('filter.select all') }}
                    </el-checkbox>
                </div>
                <div class="check-border">
                    <el-checkbox
                        size="default"
                        @change="handleDeselectAllChange"
                    >
                        {{ $t('filter.deselect all') }}
                    </el-checkbox>
                </div>
            </div>
        </div>
        <div class="options-list">
            <div
                v-for="option in filteredOptions"
                :key="option.value"
                class="option-item"
                @click="handleOptionChange(option.value, !modelValue.includes(option.value))"
            >
                <div class="option-content">
                    <Status
                        v-if="props.filterKey === 'state'"
                        :status="option.value"
                        size="small"
                    />
                    <span v-else class="option-label">{{ option.label }}</span>
                </div>
                <Checkbox
                    :modelValue="modelValue.includes(option.value)"
                    @update-model-value="(checked: boolean) => handleOptionChange(option.value, checked)"
                    @click.stop
                />
            </div>
            <el-alert 
                v-if="filteredOptions.length === 0" 
                type="info" 
                showIcon 
                :closable="false" 
                class="no-options"
            >
                {{ $t('filter.no options found') }}
                <template #icon>
                    <InformationOutline />
                </template>
            </el-alert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {Magnify, InformationOutline} from "../../utils/icons";
    import {Status} from "@kestra-io/ui-libs";
    import Checkbox from "../../../layout/Checkbox.vue";

    const props = defineProps<{
        label?: string;
        filterKey?: string;
        modelValue: string[];
        searchable?: boolean;
        placeholder?: string;
        options: {value: string; label: string}[];
    }>();

    const emits = defineEmits<{
        "apply": [];
        "reset": [];
        "update:modelValue": [value: string[]];
    }>();

    const searchQuery = ref("");

    const filteredOptions = computed(() => {
        const query = searchQuery.value.trim().toLowerCase();
        return query
            ? props.options.filter(option =>
                option.label.toLowerCase().includes(query) ||
                option.value.toLowerCase().includes(query)
            )
            : props.options;
    });

    const allSelected = computed(
        () =>
            filteredOptions.value.length > 0 &&
            filteredOptions.value.every(option => props.modelValue.includes(option.value))
    );

    const isPartiallySelected = computed(() => {
        const options = filteredOptions.value;
        if (!options.length) return false;
        const selectedCount = options.filter(opt => props.modelValue.includes(opt.value)).length;
        return selectedCount > 0 && selectedCount < options.length;
    });

    const handleSelectAllChange = (checked: boolean) => {
        const values = new Set(props.modelValue);
        filteredOptions.value.forEach(opt =>
            checked ? values.add(opt.value) : values.delete(opt.value)
        );
        emits("update:modelValue", [...values]);
    };

    const handleDeselectAllChange = (checked: boolean) => {
        if (checked) {
            const values = new Set(props.modelValue);
            filteredOptions.value.forEach(opt => values.delete(opt.value));
            emits("update:modelValue", [...values]);
        }
    };

    const handleOptionChange = (value: string, checked: boolean) =>
        emits(
            "update:modelValue",
            checked ? [...props.modelValue, value] : props.modelValue.filter(v => v !== value)
        );
</script>

<style lang="scss" scoped>
.multi-select-panel {
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
            padding: 1rem;
            padding-bottom: 0.5rem;
        }

        .controls-section {
            display: flex;
            align-items: center;
            gap: 1rem;
            padding: 0.25rem 1rem;
            margin-bottom: 8px;

            .check-border {
                border: 1px solid var(--ks-border-primary);
                border-radius: 4px;
                padding: 0 12px;
                width: calc(50% - 0.5rem);

                :deep(.el-checkbox__label) {
                    font-size: 12px;
                    color: var(--ks-content-secondary);
                }

                :deep(.el-checkbox.is-checked .el-checkbox__label) {
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
            padding: 0.5rem 1rem;
            transition: all 0.2s ease;
            cursor: pointer;
            border-bottom: 1px solid var(--ks-border-secondary);

            &:last-child {
                border-bottom: none;
            }

            &:hover {
                background-color: var(--ks-dropdown-background-hover);
            }

            .option-content {
                display: flex;
                align-items: center;

                .option-label {
                    max-width: 250px;
                    font-size: 14px;
                    font-weight: 400;
                    padding-right: 0.25rem;
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

button.status-button {
    width: 10rem;
}
</style>