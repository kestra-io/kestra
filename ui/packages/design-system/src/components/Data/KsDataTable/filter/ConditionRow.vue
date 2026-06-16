<template>
    <KsSelect
        class="cond-field"
        :modelValue="filter.key"
        :showArrow="false"
        :disabled="readOnly"
        @update:modelValue="changeField"
    >
        <KsOption v-for="key in allKeys" :key="key.key" :label="key.label" :value="key.key" />
    </KsSelect>

    <KsSelect
        class="cond-op"
        :modelValue="filter.comparator"
        :showArrow="false"
        :disabled="readOnly || comparators.length < 2"
        @update:modelValue="changeComparator"
    >
        <KsOption v-for="op in comparators" :key="op" :label="labelForComparator(op)" :value="op" />
    </KsSelect>

    <KsInput
        v-if="isTextValue"
        class="cond-value-input"
        :modelValue="textValue"
        :placeholder="keyConfig?.label"
        :disabled="readOnly"
        @update:modelValue="onText"
    />

    <KsPopover
        v-else-if="valueKind === 'select' && isMulti"
        v-model:visible="popoverOpen"
        trigger="click"
        placement="bottom-start"
        :width="320"
        :showArrow="false"
        popperClass="p-0"
    >
        <template #reference>
            <button class="cond-value-trigger" type="button" :disabled="readOnly">
                <span
                    v-if="multiDraft.length && isStatusColored"
                    ref="valueTagsRef"
                    class="value-tags"
                    :class="{'is-overflowing': hiddenTagCount > 0}"
                >
                    <span
                        v-for="v in multiDraft"
                        :key="v"
                        class="status-tag"
                        :style="statusStyle(v)"
                    >
                        <component :is="statusIcon(v)" v-if="statusIcon(v)" class="status-tag-icon" />
                        {{ labelFor(v) }}
                    </span>
                </span>
                <span v-else class="label" :class="{placeholder: !multiDraft.length}">
                    {{ multiDraft.length ? labelFor(multiDraft) : $t("filter.select_option") }}
                </span>
                <span v-if="isStatusColored && hiddenTagCount > 0" class="status-tag-more">+{{ hiddenTagCount }}</span>
                <ChevronDown class="chevron" />
            </button>
        </template>
        <FilterMultiSelect
            :modelValue="multiDraft"
            :options="options"
            :searchable="keyConfig?.searchable"
            :filterKey="keyConfig?.key"
            @update:modelValue="(v: string[]) => (multiDraft = v)"
        />
    </KsPopover>

    <KsSelect
        v-else-if="valueKind === 'select'"
        class="cond-value-select"
        :modelValue="selectModel"
        :placeholder="$t('filter.select_option')"
        :showArrow="false"
        :disabled="readOnly"
        @visible-change="(v: boolean) => v && ensureOptions()"
        @update:modelValue="onValueChange"
    >
        <KsOption v-for="opt in options" :key="opt.value" :label="opt.label" :value="opt.value" />
    </KsSelect>

    <KsPopover
        v-else
        v-model:visible="popoverOpen"
        trigger="click"
        placement="bottom-start"
        :width="300"
        :showArrow="false"
        popperClass="p-0"
    >
        <template #reference>
            <button class="cond-value-trigger" type="button" :disabled="readOnly">
                <span class="label" :class="{placeholder: !filter.valueLabel}">
                    {{ filter.valueLabel || $t("filter.select_option") }}
                </span>
                <ChevronDown class="chevron" />
            </button>
        </template>
        <FilterKVPairs
            :modelValue="kvModel"
            @update:modelValue="onKeyValue"
        />
    </KsPopover>

    <KsButton
        v-if="!readOnly"
        link
        class="cond-delete"
        :icon="Delete"
        :aria-label="$t('filter.delete filter')"
        @click="emit('remove', filter.id)"
    />
</template>

<script setup lang="ts">
    import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue"

    import FilterKVPairs from "./layout/FilterKVPairs.vue"
    import FilterMultiSelect from "./layout/FilterMultiSelect.vue"
    import {createAppliedFilter} from "./utils/filterChipFactory"
    import {
        Comparators,
        COMPARATOR_LABELS,
        TEXT_COMPARATORS,
        type AppliedFilter,
        type FilterKeyConfig,
        type FilterValue,
    } from "./utils/filterTypes"
    import {ChevronDown, Delete} from "./utils/icons"
    import {EXECUTION_STATUSES} from "../../KsExecutionStatus/types"

    const props = defineProps<{
        filter: AppliedFilter;
        allKeys: FilterKeyConfig[];
        readOnly?: boolean;
    }>()

    const emit = defineEmits<{
        update: [filter: AppliedFilter];
        remove: [id: string];
    }>()

    const options = ref<FilterValue[]>([])
    const popoverOpen = ref(false)

    const keyConfig = computed<FilterKeyConfig | null>(
        () => props.allKeys.find((key) => key.key === props.filter.key) ?? null,
    )

    const comparators = computed<Comparators[]>(
        () => keyConfig.value?.comparators ?? [props.filter.comparator],
    )

    const labelForComparator = (op: Comparators): string =>
        keyConfig.value?.comparatorLabels?.[op] ?? COMPARATOR_LABELS[op]

    const isTextValue = computed(
        () => keyConfig.value?.valueType === "text"
            || TEXT_COMPARATORS.includes(props.filter.comparator),
    )

    const valueKind = computed<"select" | "key-value" | "text">(() => {
        const type = keyConfig.value?.valueType
        if (type === "key-value") return "key-value"
        if (type === "select" || type === "multi-select" || type === "radio") return "select"
        return "text"
    })

    const isMulti = computed(() => keyConfig.value?.valueType === "multi-select")

    const isStatusColored = computed(() => keyConfig.value?.colored === true)

    const statusStyle = (value: string) => {
        const token = value.toLowerCase()
        return {
            color: `var(--ks-status-${token})`,
            backgroundColor: `var(--ks-status-background-${token})`,
        }
    }

    const statusIcon = (value: string) => EXECUTION_STATUSES[value]?.icon

    const textValue = computed(() =>
        typeof props.filter.value === "string" ? props.filter.value : "",
    )

    const selectModel = computed(() => {
        if (isMulti.value) return Array.isArray(props.filter.value) ? props.filter.value : []
        return typeof props.filter.value === "string" ? props.filter.value : ""
    })

    const kvModel = computed(() => (Array.isArray(props.filter.value) ? props.filter.value : []))

    const labelFor = (value: string | string[]): string => {
        if (Array.isArray(value)) {
            return value.map((v) => options.value.find((o) => o.value === v)?.label ?? v).join(", ")
        }
        return options.value.find((o) => o.value === value)?.label ?? value
    }

    const emitUpdate = (value: AppliedFilter["value"], valueLabel: string) =>
        emit("update", {...props.filter, value, valueLabel})

    const optionsLoaded = ref(false)

    const ensureOptions = async () => {
        if (optionsLoaded.value || !keyConfig.value?.valueProvider) return
        optionsLoaded.value = true
        try {
            options.value = await keyConfig.value.valueProvider({})
        } catch {
            options.value = []
        }
    }

    const changeField = (key: string) => {
        const config = props.allKeys.find((candidate) => candidate.key === key)
        const comparator = config?.comparators?.[0]
        if (!config || !comparator) return
        emit("update", {
            ...createAppliedFilter(config.key, config, comparator, [], "", "field"),
            id: props.filter.id,
        })
    }

    const changeComparator = (op: Comparators) =>
        emit("update", {
            ...props.filter,
            comparator: op,
            comparatorLabel: labelForComparator(op),
        })

    const onText = (value: string | number | undefined) => {
        const text = value == null ? "" : String(value)
        emitUpdate(text, text)
    }

    const onKeyValue = (value: string[]) => emitUpdate(value, value[0] ?? "")

    const multiDraft = ref<string[]>([])

    watch(() => props.filter.value, (value) => {
        multiDraft.value = Array.isArray(value) ? [...value] : []
    }, {immediate: true})

    const valueTagsRef = ref<HTMLElement | null>(null)
    const hiddenTagCount = ref(0)

    const measureTags = () => {
        const container = valueTagsRef.value
        if (!container || !isStatusColored.value) {
            hiddenTagCount.value = 0
            return
        }
        const tags = Array.from(container.querySelectorAll<HTMLElement>(".status-tag"))
        if (tags.length === 0) {
            hiddenTagCount.value = 0
            return
        }
        const limit = container.getBoundingClientRect().right
        hiddenTagCount.value = tags.filter((tag) => tag.getBoundingClientRect().right > limit + 1).length
    }

    watch(() => multiDraft.value, () => nextTick(measureTags))

    let resizeObserver: ResizeObserver | null = null
    let lastWidth = -1
    onMounted(() => {
        if (typeof ResizeObserver !== "undefined") {
            resizeObserver = new ResizeObserver(() => {
                const width = valueTagsRef.value?.clientWidth ?? 0
                if (Math.abs(width - lastWidth) < 1) return
                lastWidth = width
                measureTags()
            })
            if (valueTagsRef.value) resizeObserver.observe(valueTagsRef.value)
        }
        nextTick(measureTags)
    })
    watch(valueTagsRef, (el) => {
        if (!resizeObserver) return
        resizeObserver.disconnect()
        if (el) resizeObserver.observe(el)
    })
    onBeforeUnmount(() => resizeObserver?.disconnect())

    const onValueChange = (value: string | string[]) =>
        emitUpdate(value, labelFor(value))

    const commitMulti = () => {
        const current = Array.isArray(props.filter.value) ? props.filter.value : []
        if (JSON.stringify(current) !== JSON.stringify(multiDraft.value)) {
            emitUpdate([...multiDraft.value], labelFor(multiDraft.value))
        }
    }

    watch(() => props.filter.key, () => {
        optionsLoaded.value = false
        options.value = []
        if (!isMulti.value) ensureOptions()
    })

    watch(popoverOpen, (visible) => {
        if (visible) ensureOptions()
        else if (isMulti.value) commitMulti()
    })

    onBeforeUnmount(() => {
        if (isMulti.value) commitMulti()
    })

    onMounted(() => {
        if (!isMulti.value) ensureOptions()
    })
</script>

<style lang="scss" scoped>
.cond-field {
    flex-shrink: 0;
    width: auto;
    min-width: 8.5rem;
}

.cond-op {
    flex-shrink: 0;
    width: auto;
    min-width: 8rem;
}

.cond-op :deep(.kel-select__wrapper) {
    color: var(--ks-status-success);
}

.cond-op :deep(.kel-select__caret),
.cond-op :deep(.kel-select__suffix) {
    color: var(--ks-icon-muted);
}

.cond-value-input,
.cond-value-select {
    flex: 1;
    min-width: 8rem;
    max-width: 34rem;
}

.cond-field :deep(.kel-select__wrapper),
.cond-op :deep(.kel-select__wrapper),
.cond-value-select :deep(.kel-select__wrapper),
.cond-value-input :deep(.kel-input__inner) {
    font-size: var(--ks-font-size-xs);
}

.cond-value-trigger {
    flex: 1;
    min-width: 8rem;
    max-width: 34rem;
    display: inline-flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ks-spacing-2);
    height: 30px;
    padding: 0 var(--ks-spacing-2) 0 var(--ks-spacing-3);
    background-color: var(--ks-bg-input);
    border: 1px solid var(--ks-border-strong);
    border-radius: var(--ks-radius-base);
    box-shadow: 0 1px 2px var(--ks-shadow-element, var(--ks-shadow-surface));
    cursor: pointer;
    font-family: inherit;

    .label {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-primary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;

        &.placeholder {
            color: var(--ks-text-dim);
        }
    }

    .chevron {
        flex-shrink: 0;
        color: var(--ks-icon-muted);
        font-size: 14px;
    }
}

.value-tags {
    flex: 1;
    display: inline-flex;
    align-items: center;
    flex-wrap: nowrap;
    gap: var(--ks-spacing-1);
    min-width: 0;
    overflow: hidden;

    &.is-overflowing {
        -webkit-mask-image: linear-gradient(to right, black calc(100% - 1.5rem), transparent);
        mask-image: linear-gradient(to right, black calc(100% - 1.5rem), transparent);
    }
}

.status-tag {
    flex-shrink: 0;
    box-sizing: border-box;
    height: 1.375rem;
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    padding: 0 8px;
    border-radius: var(--ks-radius-sm);
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    white-space: nowrap;
    line-height: 1.4;
}

.status-tag-more {
    flex-shrink: 0;
    padding: 1px 6px;
    border-radius: var(--ks-radius-sm);
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    line-height: 1.4;
    white-space: nowrap;
    color: var(--ks-text-secondary);
    background-color: var(--ks-bg-hover);
}

.status-tag-icon {
    display: inline-flex;
    align-items: center;

    :deep(svg) {
        width: 14px;
        height: 14px;
    }
}

.cond-delete {
    flex-shrink: 0;
    margin: 0 !important;
    padding: var(--ks-spacing-1) !important;
    color: var(--ks-icon-muted);

    &:hover {
        color: var(--ks-status-error);
    }
}
</style>
