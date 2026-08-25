interface Label {
    key: string | null;
    value: string | null;
}

interface FilterResult {
    labels: Label[];
    error?: boolean;
}

export const filterValidLabels = (labels: Label[]): FilterResult => {
    const validLabels = labels.filter(label => label.key !== null && label.value !== null && label.key !== "" && label.value !== "")
    return validLabels.length === labels.length ? {labels} : {labels: validLabels, error: true}
}

export const FILTER_FIELD_PATTERN = /^filters(?:\[(?:and|or)]\[\d+])*\[([^\]]+)]/

export const keepSupportedFilters = (
    query: Record<string, unknown>,
    supportedFields: Set<string>,
): Record<string, unknown> => {
    return Object.fromEntries(
        Object.entries(query).filter(([key]) => {
            const match = key.match(FILTER_FIELD_PATTERN)
            return !match || supportedFields.has(match[1])
        }),
    )
}

/**
 * Keeps only bracket-format filter params. The API rejects pre-2.0 flat params such as `state=RUNNING` outright
 * (kestra-io/kestra-ee#10326), so a route query carrying one - a bookmarked pre-2.0 URL, a flat programmatic
 * navigation - must not be forwarded verbatim to an endpoint that takes nothing but filters.
 */
export const onlyBracketFilters = (
    query: Record<string, unknown>,
): Record<string, unknown> => {
    return Object.fromEntries(
        Object.entries(query).filter(([key]) => FILTER_FIELD_PATTERN.test(key)),
    )
}
