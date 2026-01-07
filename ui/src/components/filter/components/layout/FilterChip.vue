<template>
    <div class="chip" :class="{toggled: isToggled}" @click="editPopover?.toggleDialog()">
        <span class="content">
            <span class="key">{{ filter.keyLabel }}</span>
            <span v-if="!hasValue(filter.value)" class="in">in</span>
            <span v-if="!hasValue(filter.value)" class="val">any</span>
            <span v-else class="comparator" :class="{negative: isNegative}">{{ getComparatorLabel() }}</span>
            <el-tooltip
                v-if="hasValue(filter.value)"
                :content="formatTooltipValue(filter.value)"
                placement="top"
                effect="light"
            >
                <component :is="renderValueResult" />
            </el-tooltip>
        </span>
        <FilterEditPopover
            ref="editPopover"
            :filter
            :filterKey
            :shouldShowComparatorInPopper
            @update="emit('update', $event)"
            @remove="emit('remove', $event)"
        />
        <el-button
            link
            size="small"
            class="close"
            :icon="Close"
            @click.stop="emit('remove', filter.id)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, h, ref} from "vue";
    import {ElTag} from "element-plus";
    import {useValues} from "../../composables/useValues";
    import {Close} from "../../utils/icons";
    import {AppliedFilter, FilterKeyConfig, Comparators} from "../../utils/filterTypes";
    import FilterEditPopover from "./FilterEditPopover.vue";

    type FilterValueType = string | string[] | Date | {startDate: Date; endDate: Date};

    const props = defineProps<{
        filter: AppliedFilter;
        filterKey?: FilterKeyConfig | null;
    }>();

    const emit = defineEmits<{
        remove: [filterId: string];
        update: [filter: AppliedFilter];
    }>();

    const editPopover = ref<InstanceType<typeof FilterEditPopover>>();

    const {getRelativeDateLabel} = useValues("executions");

    const shouldShowComparatorInPopper = computed(
        () => (props.filterKey?.comparators?.length ?? 0) >= 2
    );

    const formatValue = (value: FilterValueType) => {
        switch (true) {
        case Array.isArray(value):
            return value.length === 1
                ? value[0]
                : [value[0], h(ElTag, {size: "small"}, () => `+${value.length - 1}`)];
        case value instanceof Date:
            return value.toLocaleDateString();
        case value && typeof value === "object" && "startDate" in value:
            return `${value.startDate.toLocaleString()} - ${value.endDate.toLocaleString()}`;
        case typeof value === "string" && /^P(T?\d+[HMD]|\d+[YMDW])/.test(value):
            return getRelativeDateLabel(value);
        default:
            return String(value);
        }
    };

    const formatTooltipValue = (value: FilterValueType) =>
        Array.isArray(value)
            ? value.join(", ")
            : String(formatValue(value));

    const hasValue = (value: FilterValueType) => {
        switch (true) {
        case Array.isArray(value):
            return value.length > 0;
        case value instanceof Date:
            return true;
        case value && typeof value === "object" && "startDate" in value:
            return true;
        default:
            return value !== "" && value != null;
        }
    };

    const getComparatorLabel = () =>
        props.filterKey
            ? props.filter.comparatorLabel
            : "in";

    const renderValueResult = computed(() =>
        h("span", {class: "value"}, formatValue(props.filter.value))
    );

    const isNegative = computed(() =>
        props.filter.comparator === Comparators.NOT_EQUALS || props.filter.comparator === Comparators.NOT_IN
    );

    const isToggled = computed(() => editPopover.value?.isDialogVisible ?? false);

    defineExpose({
        editPopover
    });
</script>

<style lang="scss" scoped>
.chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background-color: var(--ks-button-background-secondary);
    border: 1px solid var(--ks-border-primary);
    padding: 3px 12px;
    border-radius: 4px;
    cursor: pointer;
    max-width: 300px;
    max-height: 32px;
    user-select: none;

    &:hover {
        background-color: var(--ks-button-background-secondary-hover);
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
        .in,
        .val {
            font-size: 12px;
            color: var(--ks-content-primary);
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
        .in,
        .val {
            color: var(--ks-content-secondary);
        }
        .comparator {
            color: var(--ks-chart-success);
            text-transform: lowercase;

            &.negative {
                color: var(--ks-chart-failed);
            }
        }
    }
    .close {
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

    :deep(.el-tag) {
        background-color: var(--ks-tag-background);
        color: var(--ks-content-secondary);
        font-size: 10px;
        margin-left: 0.25rem;
    }
}
</style>