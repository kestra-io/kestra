<template>
    <div class="filter-edit-popper">
        <FilterComponents.Header
            :label="filterKey.label"
            @close="emits('close')"
        />
        <FilterComponents.ComparatorSelect
            :shouldShowComparator="shouldShowComparator"
            :selectedComparator="state.selectedComparator"
            :filterKey="filterKey"
            @update:selected-comparator="state.selectedComparator = $event"
        />

        <component
            v-if="valueComponent"
            :is="valueComponent.component"
            v-bind="valueComponent.props"
            v-on="valueComponent.events"
        />

        <FilterComponents.Footer
            :footerDisplayText="footerDisplayText"
            :timeRangeMode="state.timeRangeMode"
            @reset="resetState"
            @apply="handleApply"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, reactive, onMounted} from "vue";
    import {
        AppliedFilter,
        FilterKeyConfig,
        FilterValue,
        COMPARATOR_LABELS
    } from "../utils/types";

    import {useValues} from "../../composables/useValues";
    import {FilterComponents} from ".";

    const props = defineProps<{
        filter: AppliedFilter;
        filterKey: FilterKeyConfig;
        showComparatorSelection?: boolean;
    }>();

    const emits = defineEmits<{
        update: [filter: AppliedFilter];
        close: [];
    }>();

    const {getRelativeDateLabel} = useValues("executions");

    const state = reactive({
        selectedComparator: props.filter.comparator,
        textValue: "",
        selectValue: "",
        multiSelectValue: [] as string[],
        dateValue: null as Date | null,
        startDateValue: null as Date | null,
        endDateValue: null as Date | null,
        timeRangeMode: "predefined" as "predefined" | "custom",
        valueOptions: [] as FilterValue[]
    });

    const shouldShowComparator = computed(() =>
        props.filterKey.showComparatorSelection ?? props.showComparatorSelection ?? false
    );

    const valueComponent = computed(() => {
        const componentConfigs = {
            select: {
                component: FilterComponents.Select,
                props: {
                    modelValue: state.selectValue,
                    options: state.valueOptions,
                    searchable: props.filterKey.searchable,
                    label: props.filterKey.label,
                    isTimeRange: props.filterKey.key === "timeRange",
                    timeRangeMode: state.timeRangeMode,
                    startDateValue: state.startDateValue,
                    endDateValue: state.endDateValue
                },
                events: {
                    "update:modelValue": (value: string) => state.selectValue = value,
                    "update:time-range-mode": (value: "predefined" | "custom") => state.timeRangeMode = value,
                    "update:start-date-value": (value: Date | null) => state.startDateValue = value,
                    "update:end-date-value": (value: Date | null) => state.endDateValue = value
                }
            },
            text: {
                component: FilterComponents.Text,
                props: {
                    textValue: state.textValue,
                    label: props.filterKey.label
                },
                events: {
                    "update:text-value": (value: string) => state.textValue = value
                }
            },
            "multi-select": {
                component: FilterComponents.MultiSelect,
                props: {
                    modelValue: state.multiSelectValue,
                    options: state.valueOptions,
                    searchable: props.filterKey.searchable,
                    label: props.filterKey.label,
                    filterKey: props.filterKey.key
                },
                events: {
                    "update:modelValue": (value: string[]) => state.multiSelectValue = value
                }
            },
            date: {
                component: FilterComponents.DateTime,
                props: {
                    dateValue: state.dateValue,
                    label: props.filterKey.label
                },
                events: {
                    "update:date-value": (value: Date | null) => state.dateValue = value
                }
            }
        };

        return componentConfigs[props.filterKey.valueType as keyof typeof componentConfigs] || null;
    });

    const isValid = computed(() => {
        if (shouldShowComparator.value && !state.selectedComparator) return false;

        switch (props.filterKey.valueType) {
        case "text":
            return state.textValue.trim() !== "";
        case "select":
            return props.filterKey.key === "timeRange" && state.timeRangeMode === "custom"
                ? state.startDateValue !== null && state.endDateValue !== null
                : true;
        case "multi-select":
            return true;
        case "date":
            return state.dateValue !== null;
        default:
            return false;
        }
    });

    const footerDisplayText = computed(() => {
        switch (props.filterKey.valueType) {
        case "multi-select":
            return `${state.multiSelectValue.length} ${props.filterKey.label} selected`;
        case "select":
            if (state.selectValue) {
                const option = state.valueOptions.find(opt => opt.value === state.selectValue);
                return option ? option.label : state.selectValue;
            }
            return "";
        default:
            return "";
        }
    });

    const resetState = () => {
        state.textValue = "";
        state.selectValue = "";
        state.multiSelectValue = [];
        state.dateValue = null;
        state.timeRangeMode = "predefined";
        state.startDateValue = null;
        state.endDateValue = null;
    };

    const applyFilter = () => {
        if (!state.selectedComparator || !isValid.value) return;

        let filterValue: string | string[] | Date | {startDate: Date; endDate: Date};
        let valueLabel: string;

        switch (props.filterKey.valueType) {
        case "text":
            filterValue = state.textValue;
            valueLabel = state.textValue;
            break;
        case "select":
            if (props.filterKey.key === "timeRange" && state.timeRangeMode === "custom") {
                filterValue = {
                    startDate: state.startDateValue!,
                    endDate: state.endDateValue!
                };
                valueLabel = `${state.startDateValue!.toLocaleDateString()} - ${state.endDateValue!.toLocaleDateString()}`;
            } else {
                filterValue = state.selectValue;
                valueLabel = state.valueOptions.find((opt: FilterValue) => opt.value === state.selectValue)?.label || state.selectValue;
            }
            break;
        case "multi-select":
            filterValue = state.multiSelectValue;
            valueLabel = state.multiSelectValue
                .map(val => state.valueOptions.find((opt: FilterValue) => opt.value === val)?.label || val)
                .join(", ");
            break;
        case "date":
            filterValue = state.dateValue!;
            valueLabel = state.dateValue!.toLocaleDateString();
            break;
        default:
            return;
        }

        const updatedFilter: AppliedFilter = {
            ...props.filter,
            comparator: state.selectedComparator,
            comparatorLabel: COMPARATOR_LABELS[state.selectedComparator],
            value: filterValue,
            valueLabel
        };

        emits("update", updatedFilter);
    };

    const handleApply = () => {
        if (!isValid.value) return;
        applyFilter();
        emits("close");
    };

    const initializeTimeRange = () => {
        if (props.filterKey.key === "timeRange" && typeof props.filter.value === "object" && props.filter.value !== null && "startDate" in props.filter.value) {
            state.timeRangeMode = "custom";
            const dateRange = props.filter.value as {startDate: Date; endDate: Date};
            state.startDateValue = dateRange.startDate;
            state.endDateValue = dateRange.endDate;
        } else {
            state.timeRangeMode = "predefined";
            state.startDateValue = null;
            state.endDateValue = null;
        }
    };

    const initializeValueByType = () => {
        switch (props.filterKey.valueType) {
        case "text":
            state.textValue = typeof props.filter.value === "string" ? props.filter.value : "";
            break;
        case "select":
            if (typeof props.filter.value === "string") {
                const matchingOption = state.valueOptions.find(option => option.value === props.filter.value);
                state.selectValue = matchingOption ? props.filter.value : "";
            } else {
                state.selectValue = "";
            }
            break;
        case "multi-select":
            state.multiSelectValue = Array.isArray(props.filter.value) ? props.filter.value : [];
            break;
        case "date":
            state.dateValue = props.filter.value instanceof Date ? props.filter.value : null;
            break;
        }
    };

    const loadValueOptions = async () => {
        if (!props.filterKey.valueProvider) return;

        try {
            state.valueOptions = await props.filterKey.valueProvider();

            if (props.filterKey.key === "timeRange" && typeof props.filter.value === "string") {
                const currentValue = props.filter.value;
                const exists = state.valueOptions.some(option => option.value === currentValue);
                if (!exists && isTimeRangeValue(currentValue)) {
                    state.valueOptions.push({
                        value: currentValue,
                        label: getRelativeDateLabel(currentValue)
                    });
                }
            }
        } catch (error) {
            console.error("Failed to load value options:", error);
            state.valueOptions = [];
        }
    };

    const isTimeRangeValue = (value: string): boolean =>
        /^P(T?\d+[HMD]|\d+[YMDW])/.test(value);

    const initializeFilter = async () => {
        state.selectedComparator = shouldShowComparator.value ? props.filter.comparator : props.filterKey.comparators[0];
        initializeTimeRange();
        await loadValueOptions();
        initializeValueByType();
    };

    onMounted(() => {
        initializeFilter();
    });
</script>