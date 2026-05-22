<template>
    <div class="multi-select-panel">
        <div class="panel-header">
            <div v-if="props.searchable" class="search-section">
                <KsInput
                    v-model="searchQuery"
                    size="default"
                    clearable
                    :placeholder="$t('filter.search options')"
                    :prefixIcon="Magnify"
                />
            </div>
            <div class="controls-section">
                <div class="check-border">
                    <KsCheckbox
                        v-model="allSelected"
                        size="default"
                        :indeterminate="isPartiallySelected"
                        @change="handleSelectAllChange"
                    >
                        {{ $t('filter.select all') }}
                    </KsCheckbox>
                </div>
                <div class="check-border">
                    <KsCheckbox
                        size="default"
                        @change="handleDeselectAllChange"
                    >
                        {{ $t('filter.deselect all') }}
                    </KsCheckbox>
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
                    <KsExecutionStatus
                        v-if="props.filterKey === 'state'"
                        :status="option.value"
                        size="small"
                    />
                    <span v-else class="option-label">{{ option.label }}</span>
                </div>
                <KsCheckbox
                    :modelValue="modelValue.includes(option.value)"
                    @update-model-value="(checked: boolean) => handleOptionChange(option.value, checked)"
                />
            </div>
            <KsAlert
                v-if="filteredOptions.length === 0"
                type="info"
                :closable="false"
                class="no-options"
            >
                {{ $t('filter.no options found') }}
                <template #icon>
                    <InformationOutline />
                </template>
            </KsAlert>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {Magnify, InformationOutline} from "../utils/icons"
    import KsExecutionStatus from "../../../KsExecutionStatus/KsExecutionStatus.vue"

    const props = defineProps<{
        label?: string;
        filterKey?: string;
        modelValue: string[];
        searchable?: boolean;
        placeholder?: string;
        options: {value: string; label: string}[];
    }>()

    const emits = defineEmits<{
        "apply": [];
        "reset": [];
        "update:modelValue": [value: string[]];
    }>()

    const searchQuery = ref("")

    const filteredOptions = computed(() => {
        const query = searchQuery.value.trim().toLowerCase()
        return query
            ? props.options.filter(option =>
                option.label.toLowerCase().includes(query) ||
                option.value.toLowerCase().includes(query),
            )
            : props.options
    })

    const allSelected = computed(
        () =>
            filteredOptions.value.length > 0 &&
            filteredOptions.value.every(option => props.modelValue.includes(option.value)),
    )

    const isPartiallySelected = computed(() => {
        const options = filteredOptions.value
        if (!options.length) return false
        const selectedCount = options.filter(opt => props.modelValue.includes(opt.value)).length
        return selectedCount > 0 && selectedCount < options.length
    })

    const handleSelectAllChange = (checked: boolean) => {
        const values = new Set(props.modelValue)
        filteredOptions.value.forEach(opt =>
            checked ? values.add(opt.value) : values.delete(opt.value),
        )
        emits("update:modelValue", [...values])
    }

    const handleDeselectAllChange = (checked: boolean) => {
        if (checked) {
            const values = new Set(props.modelValue)
            filteredOptions.value.forEach(opt => values.delete(opt.value))
            emits("update:modelValue", [...values])
        }
    }

    const handleOptionChange = (value: string, checked: boolean) =>
        emits(
            "update:modelValue",
            checked ? [...props.modelValue, value] : props.modelValue.filter(v => v !== value),
        )
</script>

<style lang="scss" scoped>
.multi-select-panel {
    height: fit-content;
    max-height: 300px;
    display: flex;
    flex-direction: column;

    .panel-header {
        border-bottom: 1px solid var(--ks-border-default);
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
                border: 1px solid var(--ks-border-default);
                border-radius: 4px;
                padding: 0 12px;
                width: calc(50% - 0.5rem);

                :deep(.kel-checkbox__label) {
                    font-size: var(--ks-font-size-xs);
                    color: var(--ks-text-secondary);
                }

                :deep(.kel-checkbox.is-checked .kel-checkbox__label) {
                    color: var(--ks-text-primary);
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
            scrollbar-color: var(--ks-border-subtle) transparent;
        }

        .option-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0.5rem 1rem;
            transition: all 0.2s ease;
            cursor: pointer;
            border-bottom: 1px solid var(--ks-border-subtle);

            &:last-child {
                border-bottom: none;
            }

            &:hover {
                background-color: var(--ks-bg-hover-elevated);
            }

            .option-content {
                display: flex;
                align-items: center;

                .option-label {
                    max-width: 250px;
                    font-size: var(--ks-font-size-sm);
                    font-weight: 400;
                    padding-right: 0.25rem;
                }
            }
        }

        .no-options {
            text-align: center;
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-sm);

            :deep(.kel-alert__icon) {
                color: var(--ks-text-info);
                font-size: var(--ks-font-size-xl);
            }
        }
    }

    :deep(.kel-input__inner) {
        font-size: var(--ks-font-size-sm);

        &::placeholder {
            color: var(--ks-text-dim);
        }
    }
}

button.status-button {
    width: 10rem;
}
</style>
