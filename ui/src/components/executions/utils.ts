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
