<template>
    <div class="edit-popper">
        <FilterHeader
            :label="filterKey.label"
            :description="filterKey.description"
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
    import {computed, onMounted, reactive, inject} from "vue"
    import {useI18n} from "vue-i18n"
    import {
        type AppliedFilter,
        type FilterKeyConfig,
        type FilterValue,
        COMPARATOR_LABELS,
        TEXT_COMPARATORS,
        KV_COMPARATORS,
    } from "../utils/filterTypes"
    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys"
    import FilterText from "./FilterText.vue"
    import FilterRadio from "./FilterRadio.vue"
    import FilterFooter from "./FilterFooter.vue"
    import FilterHeader from "./FilterHeader.vue"
    import FilterSelect from "./FilterSelect.vue"
    import FilterKVPairs from "./FilterKVPairs.vue"
    import FilterDateTime from "./FilterDateTime.vue"
    import FilterMultiSelect from "./FilterMultiSelect.vue"
    import FilterComparatorSelect from "./FilterComparatorSelect.vue"

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
        const item = RELATIVE_DATE.find((i) => i.value === value)
        return item ? item.label : value
    }

    const props = defineProps<{
        filter: AppliedFilter;
        filterKey: FilterKeyConfig;
        showComparatorSelection?: boolean;
    }>()

    const emits = defineEmits<{
        close: [];
        remove: [filterId: string];
        update: [filter: AppliedFilter];
    }>()

    const filterContext = inject(FILTER_CONTEXT_INJECTION_KEY)

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
        timeRangeMode: "predefined" as "predefined" | "custom",
        dateFilterMode: (props.filter.meta?.dateFilter ?? props.filterKey?.dateFilterOptions?.[0]?.value ?? "") as string,
    })

    const shouldShowComparator = computed(
        () => props.filterKey?.showComparatorSelection ?? props.showComparatorSelection ?? false,
    )

    const isTextOp = computed(() =>
        TEXT_COMPARATORS.includes(state.selectedComparator) && props.filterKey?.key !== "resources",
    )

    const isKVPairFilter = computed(() =>
        props.filterKey?.valueType === "key-value",
    )

    // Server-side search is opted into by declaring an `options` param on valueProvider.
    const supportsServerSideSearch = computed(() =>
        (props.filterKey?.valueProvider?.length ?? 0) > 0,
    )

    const valueComponent = computed(() => {
        if (isTextOp.value) {
            return {
                component: FilterText,
                props: {textValue: state.textValue, label: props.filterKey?.label},
                events: {"update:text-value": (value: string) => (state.textValue = value)},
            }
        }

        // Key-value pair filters (details, labels)
        if (isKVPairFilter.value) {
            return {
                component: FilterKVPairs,
                props: {modelValue: state.keyValuePair},
                events: {"update:modelValue": (value: string[]) => (state.keyValuePair = value)},
            }
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
                    endDateValue: state.endDateValue,
                    dateFilterMode: state.dateFilterMode,
                },
                events: {
                    "update:modelValue": (value: string) => (state.selectValue = value),
                    "update:time-range-mode": (value: "predefined" | "custom") =>
                        (state.timeRangeMode = value),
                    "update:start-date-value": (value: Date | null) =>
                        (state.startDateValue = value),
                    "update:end-date-value": (value: Date | null) => (state.endDateValue = value),
                    "update:date-filter-mode": (value: string) => (state.dateFilterMode = value),
                },
            },
            text: {
                component: FilterText,
                props: {
                    textValue: state.textValue,
                    label: props.filterKey?.label,
                },
                events: {
                    "update:text-value": (value: string) => (state.textValue = value),
                },
            },
            "multi-select": {
                component: FilterMultiSelect,
                props: {
                    modelValue: state.keyValuePair,
                    options: state.valueOptions,
                    searchable: props.filterKey?.searchable,
                    label: props.filterKey?.label,
                    filterKey: props.filterKey?.key,
                    serverSideSearch: supportsServerSideSearch.value,
                },
                events: {
                    "update:modelValue": (value: string[]) => (state.keyValuePair = value),
                    "update:search": (value: string) => onSearchChange(value),
                },
            },
            date: {
                component: FilterDateTime,
                props: {
                    dateValue: state.dateValue,
                    label: props.filterKey?.label,
                },
                events: {
                    "update:date-value": (value: Date | null) => (state.dateValue = value),
                },
            },
            radio: {
                component: FilterRadio,
                props: {
                    modelValue: state.radioValue,
                    options: state.valueOptions,
                },
                events: {
                    "update:modelValue": (value: string) => (state.radioValue = value),
                },
            },
        }

        return (
            componentConfigs[props.filterKey.valueType as keyof typeof componentConfigs] || null
        )
    })

    const footerText = computed(() => {
        if (isTextOp.value) return state.textValue ?? ""

        if (isKVPairFilter.value && props.filterKey?.key === "labels") {
            return t("filter.kv_pair_selected", {count: state.keyValuePair.length})
        }

        switch (props.filterKey?.valueType) {
        case "multi-select":
            return `${state.keyValuePair.length} ${props.filterKey?.label} selected`
        case "select":
            if (state.selectValue) {
                const option = state.valueOptions?.find(opt => opt.value === state.selectValue)
                return option ? option.label : state.selectValue
            }
            return ""
        case "radio":
            return state.radioValue === "ALL" ? "Default selected" : state.radioValue
        default:
            return ""
        }
    })

    const resetState = () => {
        const defaultFilter = filterContext?.hasPreApplied(props.filterKey.key)
            ? filterContext?.getPreApplied(props.filterKey.key)
            : null

        if (defaultFilter) {
            initializeStateFromFilter(defaultFilter)
            return
        }

        Object.assign(state, {
            textValue: "",
            selectValue: "",
            keyValuePair: [],
            radioValue: "ALL",
            dateValue: null,
            timeRangeMode: "predefined",
            startDateValue: null,
            endDateValue: null,
            dateFilterMode: props.filterKey?.dateFilterOptions?.[0]?.value ?? "",
        })
    }

    const getFilterValue = () => {
        if (isTextOp.value) {
            return {value: state.textValue, label: state.textValue}
        }
        if (isKVPairFilter.value) {
            return {
                value: state.keyValuePair,
                label: state.keyValuePair[0] || "",
            }
        }

        switch (props.filterKey.valueType) {
        case "text":
            return {value: state.textValue, label: state.textValue}
        case "select":
            if (props.filterKey?.key === "timeRange" && state.timeRangeMode === "custom") {
                return {
                    value: {
                        startDate: state.startDateValue!,
                        endDate: state.endDateValue!,
                    },
                    label: `${state.startDateValue!.toLocaleDateString()} - ${state.endDateValue!.toLocaleDateString()}`,
                    meta: state.dateFilterMode ? {dateFilter: state.dateFilterMode} : undefined,
                }
            }
            return {
                value: state.selectValue,
                label:
                    state.valueOptions?.find(opt => opt.value === state.selectValue)
                        ?.label || state.selectValue,
                meta: props.filterKey?.key === "timeRange" && state.dateFilterMode
                    ? {dateFilter: state.dateFilterMode}
                    : undefined,
            }
        case "multi-select":
            return {
                value: state.keyValuePair,
                label: state.keyValuePair
                    .map(val =>
                        state.valueOptions?.find(opt => opt.value === val)?.label ?? val,
                    )
                    .join(", "),
            }
        case "date":
            return {
                value: state.dateValue ?? "",
                label: state.dateValue?.toLocaleDateString() ?? "",
            }
        case "radio":
            if (state.radioValue === "ALL") return null
            return {value: state.radioValue, label: state.radioValue}
        default:
            return null
        }
    }

    const handleApply = () => {
        if (!state.selectedComparator) return

        const filterData = getFilterValue()
        if (!filterData) {
            // The parent closes the dialog as part of handling `remove`; no extra `close` needed.
            emits("remove", props.filter.id)
            return
        }

        const updatedFilter: any = {
            ...props.filter,
            comparator: state.selectedComparator,
            comparatorLabel: props.filterKey?.comparatorLabels?.[state.selectedComparator] ?? COMPARATOR_LABELS[state.selectedComparator],
            value: filterData.value,
            valueLabel: filterData.label,
        }

        if (filterData.meta !== undefined) {
            updatedFilter.meta = filterData.meta
        } else if (props.filterKey?.key !== "timeRange") {
            delete updatedFilter.meta
        }

        if (props.filterKey?.keyLabelProvider) {
            updatedFilter.keyLabel = props.filterKey.keyLabelProvider(filterData.meta)
        }

        // The parent closes the dialog as part of handling `update`; no extra `close` needed.
        // Emitting `close` here would run the parent's empty-chip auto-remove against the stale
        // pre-update props and discard the chip we just applied.
        emits("update", updatedFilter)
    }

    const initializeStateFromFilter = (filter: AppliedFilter) => {
        state.selectedComparator = filter.comparator

        if (props.filterKey?.dateFilterOptions?.length) {
            state.dateFilterMode = filter.meta?.dateFilter
                ?? props.filterKey.dateFilterOptions[0]?.value
                ?? ""
        }

        if (
            props.filterKey?.key === "timeRange" &&
            typeof filter.value === "object" &&
            filter.value !== null &&
            "startDate" in filter.value
        ) {
            state.timeRangeMode = "custom"
            const dateRange = filter.value as {startDate: Date; endDate: Date}
            state.startDateValue = dateRange.startDate
            state.endDateValue = dateRange.endDate
        } else {
            state.timeRangeMode = "predefined"
            state.startDateValue = null
            state.endDateValue = null
        }

        const isTextOpLocal = TEXT_COMPARATORS.includes(filter.comparator) && props.filterKey?.key !== "resources"
        const isKVPair = props.filterKey?.valueType === "key-value" || (props.filterKey?.key === "labels" && KV_COMPARATORS.includes(filter.comparator))

        if (isTextOpLocal) {
            state.textValue = typeof filter.value === "string" ? filter.value : ""
        } else if (isKVPair) {
            state.keyValuePair = Array.isArray(filter.value)
                ? filter.value
                : typeof filter.value === "string"
                    ? [filter.value]
                    : []
        } else {
            switch (props.filterKey.valueType) {
            case "text":
                state.textValue = typeof filter.value === "string" ? filter.value : ""
                break
            case "multi-select":
                state.keyValuePair = Array.isArray(filter.value) ? filter.value : []
                break
            case "select":
                state.selectValue =
                    typeof filter.value === "string" &&
                    state.valueOptions.find(option => option.value === filter.value)
                        ? filter.value
                        : ""
                break
            case "date":
                state.dateValue = filter.value instanceof Date
                    ? filter.value
                    : typeof filter.value === "string"
                        ? new Date(filter.value)
                        : null
                break
            case "radio":
                state.radioValue = typeof filter.value === "string"
                    ? filter.value
                    : "ALL"
                break
            }
        }
    }

    const loadValueOptions = async (search?: string) => {
        if (!props.filterKey?.valueProvider) return

        state.valueOptions = await props.filterKey.valueProvider({search})

        if (
            props.filterKey?.key === "timeRange" &&
            typeof props.filter.value === "string"
        ) {
            const currentValue = props.filter.value
            const exists = state.valueOptions.some(
                option => option.value === currentValue,
            )
            if (!exists && /^P(T?\d+[HMD]|\d+[YMDW])/.test(currentValue)) {
                state.valueOptions.push({
                    value: currentValue,
                    label: getRelativeDateLabel(currentValue),
                })
            }
        }
    }

    let searchDebounceHandle: ReturnType<typeof setTimeout> | null = null
    const onSearchChange = (search: string) => {
        if (!supportsServerSideSearch.value) return
        if (searchDebounceHandle) clearTimeout(searchDebounceHandle)
        searchDebounceHandle = setTimeout(() => {
            loadValueOptions(search)
        }, 250)
    }

    const initializeFilter = async () => {
        state.selectedComparator = shouldShowComparator.value
            ? props.filter.comparator
            : props.filterKey.comparators[0]
        await loadValueOptions()
        initializeStateFromFilter(props.filter)
    }

    onMounted(initializeFilter)
</script>
