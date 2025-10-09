<template>
    <div class="custom-filter-chip" @click="editPopoverRef?.toggleDialog()">
        <span class="filter-content">
            <template v-if="!hasValue(filter.value)">
                <span class="filter-key">{{ filter.keyLabel }}</span>
                <span class="filter-in">in</span>
                <span class="filter-val">any</span>
            </template>
            <template v-else>
                <span class="filter-key">{{ filter.keyLabel }}</span>
                <span class="filter-comparator">{{ getComparatorLabel() }}</span>
                <el-tooltip
                    :content="formatTooltipValue(filter.value)"
                    placement="top"
                    :disabled="!hasValue(filter.value) || !Array.isArray(filter.value) || filter.value.length <= 1"
                >
                    <span class="filter-value">{{ formatValue(filter.value) }}</span>
                </el-tooltip>
            </template>
        </span>
        <FilterEditPopover
            ref="editPopoverRef"
            :filter="filter"
            :filterKey="filterKey"
            :shouldShowComparatorInDialog
            @update="handleFilterUpdate"
        />
        <el-button
            type="text"
            size="small"
            class="close-button"
            :icon="Close"
            @click.stop="emit('remove', filter.id)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useValues} from "../../composables/useValues";
    import {AppliedFilter, FilterKeyConfig, COMPARATOR_LABELS} from "../utils/types";
    import Close from "vue-material-design-icons/Close.vue";
    import FilterEditPopover from "./FilterEditPopover.vue";

    const props = defineProps<{ filter: AppliedFilter; filterKey?: FilterKeyConfig | null }>();
    
    const emit = defineEmits<{
        remove: [filterId: string];
        update: [filter: AppliedFilter];
    }>();

    const editPopoverRef = ref();

    const {getRelativeDateLabel} = useValues("executions");

    const shouldShowComparatorInDialog = computed(() => (props.filterKey?.comparators?.length ?? 0) > 2);

    const formatValue = (value: string | string[] | Date | {startDate: Date; endDate: Date}) => {
        if (Array.isArray(value)) {
            const length = value.length;
            return length === 0
                ? "Select values..."
                : length === 1
                    ? value[0]
                    : `${value[0]} +${length - 1}`;
        }
        if (value instanceof Date) {
            return value.toLocaleDateString();
        }
        if (value && typeof value === "object" && "startDate" in value) {
            return `${value.startDate.toLocaleDateString()} - ${value.endDate.toLocaleDateString()}`;
        }
        if (typeof value === "string" && /^P(T?\d+[HMD]|\d+[YMDW])/.test(value)) {
            return getRelativeDateLabel(value);
        }
        return String(value);
    };

    const formatTooltipValue = (value: string | string[] | Date | {startDate: Date; endDate: Date}) =>
        Array.isArray(value) ? value.join(", ") : formatValue(value);

    const hasValue = (value: string | string[] | Date | {startDate: Date; endDate: Date}) =>
        Array.isArray(value)
            ? value.length > 0
            : value instanceof Date ||
                (value && typeof value === "object" && "startDate" in value) ||
                (value !== "" && value != null);

    const handleFilterUpdate = (updatedFilter: AppliedFilter) => emit("update", updatedFilter);

    const getComparatorLabel = () => props.filterKey ? COMPARATOR_LABELS[props.filter.comparator] ?? props.filter.comparatorLabel : "in";

</script>

<style lang="scss" scoped>
.custom-filter-chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background-color: var(--ks-button-background-secondary);
    border: 1px solid var(--ks-border-primary);
    padding: 3px 12px;
    border-radius: 4px;
    cursor: pointer;
    
    .filter-content {
        display: flex;
        align-items: center;
        gap: 4px;

        .filter-key,
        .filter-comparator,
        .filter-value,
        .filter-in,
        .filter-val {
            font-size: 12px;
            color: var(--ks-content-primary);
            white-space: nowrap;
        }
        .filter-value {
            font-weight: 700;
        }
        .filter-in,
        .filter-val {
            color: var(--ks-content-tertiary);
        }
        .filter-comparator {
            color: var(--ks-chart-success);
            text-transform: lowercase;
        }
    }
    .close-button {
        border: none;
        background: none;
        cursor: pointer;
        padding: 0;
        color: var(--ks-content-tertiary);
        &:hover {
            color: var(--ks-content-secondary);
        }
        :deep(svg){
            font-size: 14px;
        }
    }
}
</style>