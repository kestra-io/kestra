<template>
    <div class="edit-popper">
        <FilterHeader
            :label="filterKey.label"
            @close="emits('close')"
        />
        <FilterComparatorSelect
            :shouldShowComparator
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

        <FilterFooter
            :footerText
            :timeRangeMode="state.timeRangeMode"
            @reset="resetState"
            @apply="handleApply"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, reactive, inject} from "vue";
    import {useValues} from "../../composables/useValues";
    import {
        AppliedFilter,
        COMPARATOR_LABELS,
        Comparators,
        FilterKeyConfig,
        FilterValue
    } from "../../utils/filterTypes";
    import {FILTER_CONTEXT_INJECTION_KEY} from "../../utils/filterInjectionKeys";
    import FilterText from "./FilterText.vue";
    import FilterRadio from "./FilterRadio.vue";
    import FilterFooter from "./FilterFooter.vue";
    import FilterHeader from "./FilterHeader.vue";
    import FilterSelect from "./FilterSelect.vue";
    import FilterKVPairs from "./FilterKVPairs.vue";
    import FilterDateTime from "./FilterDateTime.vue";
    import FilterMultiSelect from "./FilterMultiSelect.vue";
    import FilterComparatorSelect from "./FilterComparatorSelect.vue";

    const props = defineProps<{
        filter: AppliedFilter;
        filterKey: FilterKeyConfig;
        showComparatorSelection?: boolean;
    }>();

    const emits = defineEmits<{
        close: [];
        remove: [filterId: string];
        update: [filter: AppliedFilter];
    }>();

    const filterContext = inject(FILTER_CONTEXT_INJECTION_KEY);
    const {getRelativeDateLabel} = useValues("executions");

    const state = reactive({
        textValue: "",
        selectValue: "",
        radioValue: "ALL",
        dateValue: null as Date | null,
        keyValuePair: [] as string[],
        endDateValue: null as Date | null,
        valueOptions: [] as FilterValue[],
        startDateValue: null as Date | null,
        selectedComparator: props.filter.comparator,
        timeRangeMode: "predefined" as "predefined" | "custom"
    });

    const shouldShowComparator = computed(
        () => props.filterKey?.showComparatorSelection ?? props.showComparatorSelection ?? false
    );

    const TEXT_COMPARATORS = [
        Comparators.STARTS_WITH,
        Comparators.ENDS_WITH,
        Comparators.CONTAINS
    ];

    const LABELS_COMPARATORS = [
        Comparators.EQUALS,
        Comparators.NOT_EQUALS
    ];

    const isTextComparator = computed(() => 
        TEXT_COMPARATORS.includes(state.selectedComparator) && props.filterKey?.key !== "resources"
    );

    const isLabelKV = computed(() =>
        props.filterKey?.key === "labels" && LABELS_COMPARATORS.includes(state.selectedComparator)
    );

    const valueComponent = computed(() => {
        if (isTextComparator.value) {
            return {
                component: FilterText,
                props: {
                    textValue: state.textValue,
                    label: props.filterKey?.label
                },
                events: {
                    "update:text-value": (value: string) => (state.textValue = value)
                }
            };
        }

        // Label key-value pairs should be added via separate input fields.
        if (isLabelKV.value) {
            return {
                component: FilterKVPairs,
                props: {
                    modelValue: state.keyValuePair,
                    maxPairs: 1
                },
                events: {
                    "update:modelValue": (value: string[]) => (state.keyValuePair = value)
                }
            };
        }
        
        // valueType drives component selection
        const componentConfigs = {
            select: {
                component: FilterSelect,
                props: {
                    modelValue: state.selectValue,
                    options: state.valueOptions,
                    searchable: props.filterKey?.searchable,
                    label: props.filterKey?.label,
                    filterKey: props.filterKey,
                    timeRangeMode: state.timeRangeMode,
                    startDateValue: state.startDateValue,
                    endDateValue: state.endDateValue
                },
                events: {
                    "update:modelValue": (value: string) => (state.selectValue = value),
                    "update:time-range-mode": (value: "predefined" | "custom") =>
                        (state.timeRangeMode = value),
                    "update:start-date-value": (value: Date | null) =>
                        (state.startDateValue = value),
                    "update:end-date-value": (value: Date | null) => (state.endDateValue = value)
                }
            },
            text: {
                component: FilterText,
                props: {
                    textValue: state.textValue,
                    label: props.filterKey?.label
                },
                events: {
                    "update:text-value": (value: string) => (state.textValue = value)
                }
            },
            "multi-select": {
                component: FilterMultiSelect,
                props: {
                    modelValue: state.keyValuePair,
                    options: state.valueOptions,
                    searchable: props.filterKey?.searchable,
                    label: props.filterKey?.label,
                    filterKey: props.filterKey?.key
                },
                events: {
                    "update:modelValue": (value: string[]) => (state.keyValuePair = value)
                }
            },
            date: {
                component: FilterDateTime,
                props: {
                    dateValue: state.dateValue,
                    label: props.filterKey?.label
                },
                events: {
                    "update:date-value": (value: Date | null) => (state.dateValue = value)
                }
            },
            details: {
                component: FilterKVPairs,
                props: {
                    modelValue: state.keyValuePair
                },
                events: {
                    "update:modelValue": (value: string[]) => (state.keyValuePair = value)
                }
            },
            radio: {
                component: FilterRadio,
                props: {
                    modelValue: state.radioValue,
                    options: state.valueOptions
                },
                events: {
                    "update:modelValue": (value: string) => (state.radioValue = value)
                }
            }
        };

        return (
            componentConfigs[props.filterKey.valueType as keyof typeof componentConfigs] || null
        );
    });

    const footerText = computed(() => {
        if (isTextComparator.value) {
            return state.textValue ?? "";
        }

        if (isLabelKV.value) {
            return state.keyValuePair.length === 1
                ? state.keyValuePair[0]
                : "";
        }

        switch (props.filterKey?.valueType) {
        case "multi-select":
            return `${state.keyValuePair.length} ${props.filterKey?.label} selected`;
        case "details":
            return state.keyValuePair.length > 1
                ? `${state.keyValuePair.length} key:value pairs`
                : state.keyValuePair.length === 1
                    ? state.keyValuePair[0]
                    : "";
        case "select":
            if (state.selectValue) {
                const option = state.valueOptions?.find(opt => opt.value === state.selectValue);
                return option ? option.label : state.selectValue;
            }
            return "";
        case "radio":
            return state.radioValue === "ALL" ? "Default selected" : state.radioValue;
        default:
            return "";
        }
    });

    const resetState = () => {
        const hasPreApplied = filterContext?.hasPreApplied(props.filterKey.key);
        
        if (hasPreApplied) {
            const defaultFilter = filterContext?.getPreApplied(props.filterKey.key);
            if (defaultFilter) {
                initializeStateFromFilter(defaultFilter);
                return;
            }
        }
        
        state.textValue = "";
        state.selectValue = "";
        state.keyValuePair = [];
        state.radioValue = "ALL";
        state.dateValue = null;
        state.timeRangeMode = "predefined";
        state.startDateValue = null;
        state.endDateValue = null;
    };

    const applyFilter = () => {
        if (!state.selectedComparator) return;

        let filterValue: string | string[] | Date | {startDate: Date; endDate: Date};
        let valueLabel: string;

        if (isTextComparator.value) {
            filterValue = state.textValue;
            valueLabel = state.textValue;
        } else if (isLabelKV.value) {
            filterValue = state.keyValuePair;
            valueLabel = state.keyValuePair.length === 1
                ? state.keyValuePair[0]
                : "";
        } else {
            switch (props.filterKey.valueType) {
            case "text":
                filterValue = state.textValue;
                valueLabel = state.textValue;
                break;
            case "select":
                if (props.filterKey?.key === "timeRange" && state.timeRangeMode === "custom") {
                    filterValue = {
                        startDate: state.startDateValue!,
                        endDate: state.endDateValue!
                    };
                    valueLabel = `${state.startDateValue!.toLocaleDateString()} - ${state.endDateValue!.toLocaleDateString()}`;
                } else {
                    filterValue = state.selectValue;
                    valueLabel =
                        state.valueOptions?.find((opt: FilterValue) => opt.value === state.selectValue)
                            ?.label || state.selectValue;
                }
                break;
            case "multi-select":
                filterValue = state.keyValuePair;
                valueLabel = state.keyValuePair
                    .map(
                        val =>
                            state.valueOptions?.find((opt: FilterValue) => opt.value === val)?.label ??
                            val
                    )
                    .join(", ");
                break;
            case "details":
                filterValue = state.keyValuePair;
                valueLabel = state.keyValuePair.length > 1
                    ? `${state.keyValuePair.length} Details key/value pairs`
                    : state.keyValuePair.length === 1
                        ? state.keyValuePair[0]
                        : "";
                break;
            case "date":
                filterValue = state.dateValue ?? "";
                valueLabel = state.dateValue?.toLocaleDateString() ?? "";
                break;
            case "radio":
                if (state.radioValue === "ALL") {
                    emits("remove", props.filter.id);
                    emits("close");
                    return;
                }
                filterValue = state.radioValue;
                valueLabel = state.radioValue;
                break;
            default:
                return;
            }
        }

        emits("update", {
            ...props.filter,
            comparator: state.selectedComparator,
            comparatorLabel: COMPARATOR_LABELS[state.selectedComparator],
            value: filterValue,
            valueLabel
        });
    };

    const handleApply = () => {
        applyFilter();
        emits("close");
    };

    const initializeTimeRange = (filter: AppliedFilter) => {
        if (
            props.filterKey?.key === "timeRange" &&
            typeof filter.value === "object" &&
            filter.value !== null &&
            "startDate" in filter.value
        ) {
            state.timeRangeMode = "custom";
            const dateRange = filter.value as {startDate: Date; endDate: Date};
            state.startDateValue = dateRange.startDate;
            state.endDateValue = dateRange.endDate;
        } else {
            state.timeRangeMode = "predefined";
            state.startDateValue = null;
            state.endDateValue = null;
        }
    };

    const initializeStateFromFilter = (filter: AppliedFilter) => {
        state.selectedComparator = filter.comparator;
        initializeTimeRange(filter);
        
        const isTextComp = TEXT_COMPARATORS.includes(filter.comparator) && props.filterKey?.key !== "resources";
        const isLabelKVInit = props.filterKey?.key === "labels" && LABELS_COMPARATORS.includes(filter.comparator);
        
        if (isTextComp) {
            state.textValue = typeof filter.value === "string" ? filter.value : "";
        } else if (isLabelKVInit) {
            state.keyValuePair = Array.isArray(filter.value) 
                ? filter.value 
                : typeof filter.value === "string" 
                    ? [filter.value] 
                    : [];
        } else {
            switch (props.filterKey.valueType) {
            case "text":
                state.textValue = typeof filter.value === "string" ? filter.value : "";
                break;
            case "select":
                if (typeof filter.value === "string") {
                    const matchingOption = state.valueOptions.find(
                        option => option.value === filter.value
                    );
                    state.selectValue = matchingOption ? filter.value : "";
                } else {
                    state.selectValue = "";
                }
                break;
            case "multi-select":
            case "details":
                state.keyValuePair = Array.isArray(filter.value) ? filter.value : [];
                break;
            case "date":
                state.dateValue = filter.value instanceof Date 
                    ? filter.value 
                    : typeof filter.value === "string" 
                        ? new Date(filter.value) 
                        : null;
                break;
            case "radio":
                state.radioValue = typeof filter.value === "string" ? filter.value : "ALL";
                break;
            }
        }
    };

    const initializeValueByType = () => {
        initializeStateFromFilter(props.filter);
    };

    const loadValueOptions = async () => {
        if (!props.filterKey?.valueProvider) return;

        state.valueOptions = await props.filterKey.valueProvider();

        if (props.filterKey?.key === "timeRange" && typeof props.filter.value === "string") {
            const currentValue = props.filter.value;
            const exists = state.valueOptions.some(option => option.value === currentValue);
            if (!exists && isTimeRangeValue(currentValue)) {
                state.valueOptions.push({
                    value: currentValue,
                    label: getRelativeDateLabel(currentValue)
                });
            }
        }
    };

    const isTimeRangeValue = (value: string): boolean =>
        /^P(T?\d+[HMD]|\d+[YMDW])/.test(value);

    const initializeFilter = async () => {
        state.selectedComparator = shouldShowComparator.value
            ? props.filter.comparator
            : props.filterKey.comparators[0];
        await loadValueOptions();
        initializeValueByType();
    };

    onMounted(initializeFilter);
</script>