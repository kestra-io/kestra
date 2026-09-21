import type {Label} from "@kestra-io/kestra-sdk"

/** A label row as the editor holds it, where either half may still be blank. */
interface DraftLabel {
    key: string | null;
    value: string | null;
}

interface FilterResult {
    labels: Label[];
    error?: boolean;
}

export const filterValidLabels = (labels: DraftLabel[]): FilterResult => {
    const validLabels = labels.filter((label): label is Label => Boolean(label.key) && Boolean(label.value))
    return validLabels.length === labels.length ? {labels: validLabels} : {labels: validLabels, error: true}
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
