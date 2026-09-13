export const FILTERABLE_LOG_FIELDS = {
    namespace: {for: "IN", out: "NOT_IN"},
    flowId: {for: "EQUALS", out: "NOT_EQUALS"},
    taskId: {for: "EQUALS", out: "NOT_EQUALS"},
    triggerId: {for: "EQUALS", out: "NOT_EQUALS"},
    taskRunId: {for: "EQUALS", out: "NOT_EQUALS"},
    attemptNumber: {for: "EQUALS", out: "NOT_EQUALS"},
} as const

export const isFilterableLogField = (field: string): boolean =>
    Object.prototype.hasOwnProperty.call(FILTERABLE_LOG_FIELDS, field)

export const buildValueFilterQuery = (
    currentQuery: Record<string, any>,
    field: string,
    value: string,
    negate: boolean,
    pageKey = "page",
): Record<string, any> | null => {
    const comparators = FILTERABLE_LOG_FIELDS[field as keyof typeof FILTERABLE_LOG_FIELDS]
    if (!comparators) return null

    const comparator = negate ? comparators.out : comparators.for
    const fieldPrefix = `filters[${field}][`
    const cleaned = Object.fromEntries(
        Object.entries(currentQuery).filter(([k]) => !k.startsWith(fieldPrefix)),
    )
    return {...cleaned, [`filters[${field}][${comparator}]`]: value, [pageKey]: "1"}
}
