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
                <button
                    type="button"
                    class="select-all-btn"
                    role="checkbox"
                    :aria-checked="allSelected ? 'true' : (isPartiallySelected ? 'mixed' : 'false')"
                    @click="handleSelectAllChange(!allSelected)"
                >
                    <KsCheckbox
                        class="select-all-box"
                        :modelValue="allSelected"
                        size="default"
                        :indeterminate="isPartiallySelected"
                        tabindex="-1"
                        aria-hidden="true"
                    >
                        {{ $t('filter.select all') }}
                    </KsCheckbox>
                </button>
                <button
                    v-if="modelValue.length > 0"
                    type="button"
                    class="clear-btn"
                    @click="handleDeselectAll"
                >
                    {{ $t('filter.deselect all') }}
                </button>
            </div>
        </div>
        <div class="options-list">
            <template v-if="isStateFilter">
                <template v-if="visibleGroups.length > 0">
                    <template v-for="group in visibleGroups" :key="group.key">
                        <div
                            v-if="isSingleGroup(group.key)"
                            class="state-group state-single"
                        >
                            <div class="group-row">
                                <button
                                    type="button"
                                    class="group-toggle"
                                    role="checkbox"
                                    :aria-checked="modelValue.includes(group.options[0].value) ? 'true' : 'false'"
                                    :aria-label="group.options[0].value"
                                    @click="handleOptionChange(group.options[0].value, !modelValue.includes(group.options[0].value))"
                                >
                                    <KsCheckbox
                                        class="group-toggle-box"
                                        :modelValue="modelValue.includes(group.options[0].value)"
                                        size="default"
                                        tabindex="-1"
                                        aria-hidden="true"
                                    />
                                </button>
                                <button
                                    type="button"
                                    class="group-label-btn"
                                    @click="handleOptionChange(group.options[0].value, !modelValue.includes(group.options[0].value))"
                                >
                                    <KsExecutionStatus :status="group.options[0].value" size="small" />
                                </button>
                            </div>
                        </div>
                        <div
                            v-else
                            class="state-group"
                            :class="{'state-group-expanded': expandedGroups.has(group.key)}"
                        >
                            <div class="group-row">
                                <button
                                    type="button"
                                    class="group-toggle"
                                    role="checkbox"
                                    :aria-checked="isGroupFullySelected(group) ? 'true' : (isGroupPartiallySelected(group) ? 'mixed' : 'false')"
                                    :aria-label="$t(group.labelKey)"
                                    @click="setGroupSelection(group, !isGroupFullySelected(group))"
                                >
                                    <KsCheckbox
                                        class="group-toggle-box"
                                        :modelValue="isGroupFullySelected(group)"
                                        :indeterminate="isGroupPartiallySelected(group)"
                                        size="default"
                                        tabindex="-1"
                                        aria-hidden="true"
                                    />
                                </button>
                                <button
                                    type="button"
                                    class="group-label-btn"
                                    @click.stop="toggleGroupExpand(group.key)"
                                >
                                    <span
                                        class="group-swatch"
                                        :style="{background: `var(${group.token})`}"
                                    />
                                    <span class="group-label-text">{{ $t(group.labelKey) }}</span>
                                    <span
                                        class="group-count"
                                        :class="{active: isGroupPartiallySelected(group) || isGroupFullySelected(group)}"
                                    >
                                        {{ groupSelectedCount(group) }}/{{ group.options.length }}
                                    </span>
                                </button>
                                <button
                                    type="button"
                                    class="group-expand-btn"
                                    :aria-expanded="expandedGroups.has(group.key)"
                                    :aria-label="`${expandedGroups.has(group.key) ? $t('collapse') : $t('expand')} ${$t(group.labelKey)}`"
                                    @click.stop="toggleGroupExpand(group.key)"
                                >
                                    <ChevronDown class="expand-icon" />
                                </button>
                            </div>
                            <div v-if="expandedGroups.has(group.key)" class="group-options">
                                <div
                                    v-for="option in group.options"
                                    :key="option.value"
                                    class="option-item"
                                    @click="handleOptionChange(option.value, !modelValue.includes(option.value))"
                                >
                                    <div class="option-content">
                                        <KsExecutionStatus
                                            :status="option.value"
                                            size="small"
                                        />
                                    </div>
                                    <KsCheckbox
                                        :modelValue="modelValue.includes(option.value)"
                                        @update-model-value="(checked: boolean) => handleOptionChange(option.value, checked)"
                                    />
                                </div>
                            </div>
                        </div>
                    </template>
                </template>
                <KsAlert
                    v-else
                    type="info"
                    :closable="false"
                    class="no-options"
                >
                    {{ $t('filter.no options found') }}
                    <template #icon>
                        <InformationOutline />
                    </template>
                </KsAlert>
            </template>
            <template v-else>
                <div
                    v-for="option in filteredOptions"
                    :key="option.value"
                    class="option-item"
                    @click="handleOptionChange(option.value, !modelValue.includes(option.value))"
                >
                    <div class="option-content">
                        <span class="option-label">{{ option.label }}</span>
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
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {Magnify, InformationOutline, ChevronDown} from "../utils/icons"
    import KsExecutionStatus from "../../../KsExecutionStatus/KsExecutionStatus.vue"
    import {resolveStateGroups, type ResolvedGroup} from "../utils/stateGroups"

    const props = defineProps<{
        label?: string;
        filterKey?: string;
        modelValue: string[];
        searchable?: boolean;
        placeholder?: string;
        options: {value: string; label: string}[];
        /** When true, emit `update:search` instead of filtering options locally — parent reloads via valueProvider. */
        serverSideSearch?: boolean;
    }>()

    const emits = defineEmits<{
        "apply": [];
        "reset": [];
        "update:modelValue": [value: string[]];
        "update:search": [search: string];
    }>()

    const searchQuery = ref("")
    const expandedGroups = ref<Set<string>>(new Set())

    watch(searchQuery, (value) => {
        if (props.serverSideSearch) {
            emits("update:search", value)
        }
    })

    const isStateFilter = computed(() => props.filterKey === "state")

    const filteredOptions = computed(() => {
        if (props.serverSideSearch) return props.options
        const query = searchQuery.value.trim().toLowerCase()
        return query
            ? props.options.filter(option =>
                option.label.toLowerCase().includes(query) ||
                option.value.toLowerCase().includes(query),
            )
            : props.options
    })

    const allGroups = computed(() => resolveStateGroups(props.options))

    const singleStateKeys = computed(() =>
        new Set(allGroups.value.filter(g => g.options.length === 1).map(g => g.key)),
    )
    const isSingleGroup = (key: string) => singleStateKeys.value.has(key)

    const visibleGroups = computed(() => {
        const query = searchQuery.value.trim().toLowerCase()
        if (!query) return allGroups.value
        return allGroups.value.flatMap(group => {
            const matched = group.options.filter(o =>
                o.label.toLowerCase().includes(query) || o.value.toLowerCase().includes(query),
            )
            if (matched.length === 0) return []
            return [{...group, options: matched}]
        })
    })

    const visibleGroupKeys = computed(() => visibleGroups.value.map(g => g.key).join("|"))

    watch(visibleGroupKeys, () => {
        const query = searchQuery.value.trim().toLowerCase()
        if (!query) return
        const next = new Set(expandedGroups.value)
        visibleGroups.value.forEach(g => next.add(g.key))
        expandedGroups.value = next
    })

    const allSelected = computed(() => {
        const options = isStateFilter.value ? props.options : filteredOptions.value
        return options.length > 0 && options.every(o => props.modelValue.includes(o.value))
    })

    const isPartiallySelected = computed(() => {
        const options = isStateFilter.value ? props.options : filteredOptions.value
        if (!options.length) return false
        const count = options.filter(o => props.modelValue.includes(o.value)).length
        return count > 0 && count < options.length
    })

    const handleSelectAllChange = (checked: boolean) => {
        const targets = isStateFilter.value ? props.options : filteredOptions.value
        const values = new Set(props.modelValue)
        targets.forEach(opt => checked ? values.add(opt.value) : values.delete(opt.value))
        emits("update:modelValue", [...values])
    }

    const handleDeselectAll = () => {
        const targets = isStateFilter.value ? props.options : filteredOptions.value
        const values = new Set(props.modelValue)
        targets.forEach(opt => values.delete(opt.value))
        emits("update:modelValue", [...values])
    }

    const handleOptionChange = (value: string, checked: boolean) =>
        emits(
            "update:modelValue",
            checked ? [...props.modelValue, value] : props.modelValue.filter(v => v !== value),
        )

    const toggleGroupExpand = (key: string) => {
        const next = new Set(expandedGroups.value)
        if (next.has(key)) {
            next.delete(key)
        } else {
            next.add(key)
        }
        expandedGroups.value = next
    }

    const groupSelectedCount = (group: ResolvedGroup) =>
        group.options.filter(o => props.modelValue.includes(o.value)).length

    const isGroupFullySelected = (group: ResolvedGroup) =>
        group.options.length > 0 && group.options.every(o => props.modelValue.includes(o.value))

    const isGroupPartiallySelected = (group: ResolvedGroup) => {
        const count = groupSelectedCount(group)
        return count > 0 && count < group.options.length
    }

    const setGroupSelection = (group: ResolvedGroup, checked: boolean) => {
        const values = new Set(props.modelValue)
        group.options.forEach(o => checked ? values.add(o.value) : values.delete(o.value))
        emits("update:modelValue", [...values])
    }
</script>

<style lang="scss" scoped>
.multi-select-panel {
    height: fit-content;
    max-height: 360px;
    display: flex;
    flex-direction: column;

    .panel-header {
        border-bottom: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 1;
        background-color: var(--ks-bg-surface);

        .search-section {
            padding: 1rem;
            padding-bottom: 0.5rem;
        }

        .controls-section {
            display: flex;
            align-items: center;
            gap: 0;
            padding: var(--ks-spacing-2) var(--ks-spacing-4);
            margin-bottom: var(--ks-spacing-1);

            .select-all-btn {
                background: none;
                border: none;
                padding: 0;
                cursor: pointer;
                display: flex;
                align-items: center;
                flex: 1;
                font-family: inherit;

                .select-all-box {
                    pointer-events: none;
                }

                :deep(.kel-checkbox__label) {
                    font-size: var(--ks-font-size-xs);
                    color: var(--ks-text-secondary);
                }

                :deep(.kel-checkbox.is-checked .kel-checkbox__label) {
                    color: var(--ks-text-primary);
                }
            }

            .clear-btn {
                background: none;
                border: none;
                padding: 0;
                cursor: pointer;
                font-family: inherit;
                font-size: var(--ks-font-size-xs);
                font-weight: 600;
                color: var(--ks-text-link);

                &:hover {
                    opacity: 0.8;
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
            gap: var(--ks-spacing-3);
            padding: var(--ks-spacing-2) var(--ks-spacing-4);
            transition: background 0.15s ease;
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
                flex: 1;
                min-width: 0;

                .option-label {
                    font-size: var(--ks-font-size-sm);
                    font-weight: 400;
                    word-break: break-word;
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

        .state-group {
            border-bottom: 1px solid var(--ks-border-subtle);

            &:last-child {
                border-bottom: none;
            }

            .group-row {
                display: flex;
                align-items: center;
                padding: var(--ks-spacing-1) var(--ks-spacing-2) var(--ks-spacing-1) var(--ks-spacing-3);

                &:hover {
                    background-color: var(--ks-bg-hover);
                }
            }

            .group-toggle {
                background: none;
                border: none;
                padding: var(--ks-spacing-1);
                cursor: pointer;
                display: flex;
                align-items: center;
                flex-shrink: 0;

                .group-toggle-box {
                    pointer-events: none;
                }
            }

            .group-label-btn {
                display: flex;
                align-items: center;
                gap: var(--ks-spacing-2);
                flex: 1;
                min-width: 0;
                background: none;
                border: none;
                cursor: pointer;
                font-family: inherit;
                text-align: left;
                padding: var(--ks-spacing-1) var(--ks-spacing-2);

                &:focus-visible {
                    outline: 2px solid var(--ks-border-focus);
                    outline-offset: 1px;
                    border-radius: var(--ks-radius-xs);
                }
            }

            .group-swatch {
                width: 9px;
                height: 9px;
                border-radius: 3px;
                flex-shrink: 0;
            }

            .group-label-text {
                font-size: var(--ks-font-size-sm);
                font-weight: 600;
                color: var(--ks-text-primary);
                flex: 1;
                min-width: 0;
            }

            .group-count {
                font-size: var(--ks-font-size-xs);
                font-weight: 600;
                padding: 1px var(--ks-spacing-2);
                border-radius: 999px;
                background: var(--ks-bg-tag);
                color: var(--ks-text-secondary);
                white-space: nowrap;
                flex-shrink: 0;

                &.active {
                    background: var(--ks-bg-tag-active);
                    color: var(--ks-text-link);
                }
            }

            .group-expand-btn {
                width: 28px;
                height: 28px;
                flex-shrink: 0;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                background: none;
                border: none;
                cursor: pointer;
                border-radius: var(--ks-radius-xs);
                color: var(--ks-icon-muted);

                &:hover {
                    background: var(--ks-bg-hover-elevated);
                    color: var(--ks-icon-default);
                }

                &:focus-visible {
                    outline: 2px solid var(--ks-border-focus);
                    outline-offset: 1px;
                }

                .expand-icon {
                    transition: transform 0.15s ease;
                }
            }

            &.state-group-expanded .expand-icon {
                transform: rotate(0deg);
            }

            &:not(.state-group-expanded) .expand-icon {
                transform: rotate(-90deg);
            }

            .group-options {
                padding-bottom: var(--ks-spacing-1);

                .option-item {
                    padding-left: var(--ks-spacing-6);
                    border-bottom: 1px solid var(--ks-border-subtle);

                    &:last-child {
                        border-bottom: none;
                    }
                }
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
