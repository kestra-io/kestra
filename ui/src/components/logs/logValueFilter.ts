export const FILTERABLE_LOG_FIELDS: Record<string, {for: string; out: string}> = {
    namespace: {for: "IN", out: "NOT_IN"},
    flowId: {for: "EQUALS", out: "NOT_EQUALS"},
    taskId: {for: "EQUALS", out: "NOT_EQUALS"},
    triggerId: {for: "EQUALS", out: "NOT_EQUALS"},
    taskRunId: {for: "EQUALS", out: "NOT_EQUALS"},
    attemptNumber: {for: "EQUALS", out: "NOT_EQUALS"},
}

export const isFilterableLogField = (field: string): boolean =>
    Object.prototype.hasOwnProperty.call(FILTERABLE_LOG_FIELDS, field)

export const buildValueFilterQuery = (
    currentQuery: Record<string, any>,
    field: string,
    value: string,
    negate: boolean,
    pageKey = "page",
): Record<string, any> | null => {
    const comparators = FILTERABLE_LOG_FIELDS[field]
    if (!comparators) return null

    const comparator = negate ? comparators.out : comparators.for
    return {...currentQuery, [`filters[${field}][${comparator}]`]: value, [pageKey]: "1"}
}
