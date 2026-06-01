<template>
    <div
        class="chip"
        :class="{toggled: isToggled}"
        @click="editPopover?.toggleDialog()"
        @auxclick="onAuxClick"
        @mousedown="onMouseDown"
    >
        <span class="content">
            <span class="key">{{ filter.keyLabel }}</span>
            <span v-if="!hasValue(filter.value)" class="in">{{ t("filter.in any") }}</span>
            <span v-else-if="shouldShowComparatorLabel" class="comparator" :class="{negative: isNegative}">{{ getComparatorLabel() }}</span>
            <KsTooltip
                v-if="hasValue(filter.value)"
                :content="formatTooltipValue(filter.value)"
                placement="top"
            >
                <component :is="renderValueResult" />
            </KsTooltip>
        </span>
        <FilterEditPopover
            ref="editPopover"
            :filter
            :filterKey
            :shouldShowComparatorInPopper
            @update="emit('update', $event)"
            @remove="emit('remove', $event)"
        />
        <KsButton
            link
            size="small"
            class="close"
            :icon="Close"
            @click.stop="emit('remove', filter.id)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, h, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import KsTag from "../../../KsTag/KsTag.vue"
    import {Close} from "../utils/icons"
    import {
        type AppliedFilter,
        type FilterKeyConfig,
        Comparators,
    } from "../utils/filterTypes"
    import {isDateRangeValue} from "../utils/filterChipFactory"
    import FilterEditPopover from "./FilterEditPopover.vue"

    const {t} = useI18n({useScope: "global"})

    const RELATIVE_DATE = [
        {label: t("datepicker.last5minutes"), value: "PT5M"},
        {label: t("datepicker.last15minutes"), value: "PT15M"},
        {label: t("datepicker.last1hour"), value: "PT1H"},
        {label: t("datepicker.last12hours"), value: "PT12H"},
        {label: t("datepicker.last24hours"), value: "PT24H"},
        {label: t("datepicker.last48hours"), value: "PT48H"},
        {label: t("datepicker.last7days"), value: "PT168H"},
        {label: t("datepicker.last30days"), value: "P30D"},
        {label: t("datepicker.last365days"), value: "PT8760H"},
    ]

    const getRelativeDateLabel = (value: string): string => {
        const found = RELATIVE_DATE.find((item) => item.value === value)
        return found ? found.label : value
    }

    type FilterValueType = string | string[] | Date | {startDate: Date; endDate: Date};

    const props = defineProps<{
        filter: AppliedFilter;
        filterKey?: FilterKeyConfig | null;
    }>()

    const emit = defineEmits<{
        remove: [filterId: string];
        update: [filter: AppliedFilter];
    }>()

    const editPopover = ref<InstanceType<typeof FilterEditPopover>>()

    const onAuxClick = (event: MouseEvent) => {
        if (event.button !== 1) return
        event.preventDefault()
        event.stopPropagation()
        emit("remove", props.filter.id)
    }

    const onMouseDown = (event: MouseEvent) => {
        if (event.button === 1) event.preventDefault()
    }

    const shouldShowComparatorInPopper = computed(
        () => (props.filterKey?.comparators?.length ?? 0) >= 2,
    )
    const shouldShowComparatorLabel = computed(
        () => (props.filterKey?.comparators?.length ?? 0) >= 2,
    )

    const formatValue = (value: FilterValueType) => {
        switch (true) {
        case Array.isArray(value):
            return value.length === 1
                ? value[0]
                : [value[0], h(KsTag, {size: "small"}, () => `+${value.length - 1}`)]
        case value instanceof Date:
            return value.toLocaleDateString()
        case value && typeof value === "object" && "startDate" in value:
            return `${value.startDate.toLocaleString()} - ${value.endDate.toLocaleString()}`
        case typeof value === "string" && /^P(T?\d+[HMD]|\d+[YMDW])/.test(value):
            return getRelativeDateLabel(value)
        default:
            return String(value)
        }
    }

    const formatTooltipValue = (value: FilterValueType) =>
        Array.isArray(value)
            ? value.join(", ")
            : String(formatValue(value))

    const hasValue = (value: FilterValueType) => {
        switch (true) {
        case Array.isArray(value):
            return value.length > 0
        case value instanceof Date:
            return true
        case value && typeof value === "object" && "startDate" in value:
            return true
        default:
            return value !== "" && value != null
        }
    }

    const getComparatorLabel = () => {
        if (!props.filterKey) return "in"
        // Range filters ({startDate, endDate}) have no real comparator — render a
        // localized "between" label instead of the model's baked comparatorLabel.
        if (isDateRangeValue(props.filter.value)) return t("filter.is_between")
        return props.filter.comparatorLabel
    }

    const renderValueResult = computed(() =>
        h("span", {class: "value"}, formatValue(props.filter.value)),
    )

    const isNegative = computed(() =>
        props.filter.comparator === Comparators.NOT_EQUALS || props.filter.comparator === Comparators.NOT_IN,
    )

    const isToggled = computed(() => editPopover.value?.isDialogVisible ?? false)

    defineExpose({
        editPopover,
    })
</script>

<style lang="scss" scoped>
.chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background-color: var(--ks-btn-secondary-bg-default);
    border: 1px solid var(--ks-border-default);
    padding: 3px 12px;
    border-radius: 4px;
    cursor: pointer;
    max-width: 300px;
    min-height: 32px;
    max-height: 32px;
    user-select: none;

    &:hover {
        background-color: var(--ks-btn-secondary-bg-hover);
    }

    &.toggled {
        border-color: var(--ks-border-success);
    }

    .content {
        display: flex;
        align-items: center;
        gap: 4px;
        flex: 1;
        min-width: 0;

        .key,
        .comparator,
        .value,
        .in {
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-primary);
            white-space: nowrap;
            display: flex;
            align-items: center;
        }
        .value {
            font-weight: 700;
            overflow: hidden;
            text-overflow: ellipsis;
            min-width: 0;
            flex-shrink: 1;
        }
        .in {
            color: var(--ks-text-secondary);
        }
        .comparator {
            color: var(--ks-status-success);
            text-transform: lowercase;

            &.negative {
                color: var(--ks-status-error);
            }
        }
    }
    .close {
        border: none;
        background: none;
        cursor: pointer;
        padding: 0;
        margin: 0;
        color: var(--ks-text-dim);
        font-size: var(--ks-font-size-base);
        &:hover {
            color: var(--ks-text-secondary);
        }
        :deep(svg) {
            font-size: var(--ks-font-size-base);
        }
    }

    :deep(.kel-tag) {
        background-color: var(--ks-bg-tag);
        color: var(--ks-text-secondary);
        font-size: 10px;
        margin-left: 0.25rem;
    }
}
</style>
