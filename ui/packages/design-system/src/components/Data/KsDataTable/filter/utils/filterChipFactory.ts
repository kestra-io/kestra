import {
    type AppliedFilter,
    type FilterKeyConfig,
    COMPARATOR_LABELS,
    Comparators,
    TEXT_COMPARATORS,
} from "./filterTypes"
import {type DecodedParam, keyOfComparator} from "./helpers"
import {TIME_RANGE_KEY} from "./constants"

export const createAppliedFilter = (
    key: string,
    config: FilterKeyConfig | undefined,
    comparator: Comparators,
    value: AppliedFilter["value"],
    valueLabel: string,
    idSuffix: string,
    meta?: Record<string, string>,
): AppliedFilter => ({
    id: `${key}-${idSuffix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    key,
    keyLabel: config?.keyLabelProvider ? config.keyLabelProvider(meta) : (config?.label ?? key),
    comparator,
    comparatorLabel: config?.comparatorLabels?.[comparator] ?? COMPARATOR_LABELS[comparator],
    value,
    valueLabel,
    ...(meta ? {meta} : {}),
})

/**
 * True when a filter value is a date range ({@code {startDate, endDate}}).
 * Range filters render a localized "between" comparator label at display time
 * (see {@code FilterChip.vue}/{@code SaveFilters.vue}) rather than baking an
 * untranslatable English string into the model here.
 */
export const isDateRangeValue = (
    value: AppliedFilter["value"],
): value is {startDate: Date; endDate: Date} =>
    !!value && typeof value === "object" && "startDate" in value && "endDate" in value

export const createTimeRangeFilter = (
    config: FilterKeyConfig,
    startDate: Date,
    endDate: Date,
    comparator = Comparators.EQUALS,
    meta?: Record<string, string>,
): AppliedFilter =>
    createAppliedFilter(
        TIME_RANGE_KEY,
        config,
        comparator,
        {startDate, endDate},
        `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`,
        keyOfComparator(comparator),
        meta,
    )

export const createCustomRangeFilter = (
    key: string,
    config: FilterKeyConfig,
    startDate: Date,
    endDate: Date,
    comparator = Comparators.GREATER_THAN_OR_EQUAL_TO,
): AppliedFilter =>
    createAppliedFilter(
        key,
        config,
        comparator,
        {startDate, endDate},
        `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`,
        keyOfComparator(comparator),
    )

export const processFieldValue = (
    config: FilterKeyConfig,
    params: DecodedParam[],
    comparator: Comparators,
): {value: AppliedFilter["value"]; valueLabel: string} => {
    const isTextOp = TEXT_COMPARATORS.includes(comparator)

    if (config?.valueType === "key-value") {
        const combinedValue = params.map(p => p?.value as string)
        return {
            value: combinedValue,
            valueLabel: combinedValue.length > 1
                ? `${combinedValue[0]} +${combinedValue.length - 1}`
                : combinedValue[0] ?? "",
        }
    }

    if (config?.valueType === "multi-select" && !isTextOp) {
        const combinedValue = params.flatMap(p =>
            Array.isArray(p?.value) ? p.value : (p?.value as string)?.split(",") ?? [],
        )
        return {
            value: combinedValue,
            valueLabel: combinedValue.join(", "),
        }
    }

    let value: AppliedFilter["value"] = Array.isArray(params[0]?.value)
        ? params[0].value[0]
        : (params[0]?.value as string)

    if (config?.valueType === "date" && typeof value === "string") {
        value = new Date(value)
    } else if (config?.valueType === "time-range" && typeof value === "string" && !/^P/i.test(value)) {
        // A custom single absolute date for a time-range field. Predefined relative durations
        // (PT24H, P30D, …) start with "P" and must stay as the raw duration string.
        value = new Date(value)
    }

    return {
        value,
        valueLabel: value instanceof Date ? value.toLocaleDateString() : String(value),
    }
}

export const resolveDefaultVisibleValue = (key: FilterKeyConfig): AppliedFilter["value"] => {
    const value = typeof key.defaultValue === "function"
        ? key.defaultValue()
        : key.defaultValue
    if (value !== undefined) return value
    return key.valueType === "multi-select" ? [] : ""
}

export const defaultVisibleValueLabel = (value: AppliedFilter["value"]): string => {
    if (Array.isArray(value)) return value.join(", ")
    if (value && typeof value === "object" && "startDate" in value && "endDate" in value) {
        return `${value.startDate.toLocaleDateString()} - ${value.endDate.toLocaleDateString()}`
    }
    if (value instanceof Date) return value.toLocaleDateString()
    return value?.toString?.() ?? ""
}

export const createDefaultVisibleFilters = (
    configKeys: FilterKeyConfig[] | undefined,
    excludedKeys: Set<string>,
    dismissedKeys: Set<string>,
): AppliedFilter[] =>
    configKeys
        ?.filter(key =>
            key.visibleByDefault
            && !excludedKeys.has(key.key)
            && !dismissedKeys.has(key.key),
        )
        .map(key => {
            const comparator = key.comparators[0] ?? Comparators.EQUALS
            const value = resolveDefaultVisibleValue(key)
            const valueLabel = defaultVisibleValueLabel(value)
            return {
                ...createAppliedFilter(key.key, key, comparator, value, valueLabel, "default"),
                isDefaultVisible: true,
            } as AppliedFilter
        }) ?? []
