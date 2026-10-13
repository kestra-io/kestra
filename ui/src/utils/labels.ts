import type {LocationQuery} from "vue-router"
import {parseFilterKey} from "@kestra-io/design-system"

// Negative operations (NOT_EQUALS, NOT_IN, ...) exclude a label, so they must not reveal it.
const POSITIVE_OPERATIONS = new Set(["EQUALS", "IN"])

const hasQueryValue = (value: LocationQuery[string]): boolean => {
    if (Array.isArray(value)) {
        return value.some(item => item !== null && item !== "")
    }

    return value !== null && value !== undefined && value !== ""
}

export const filterHiddenLabels = <T extends {key: string; value: string}>(
    labels: T[] | undefined,
    hiddenPrefixes: string[] = [],
    query: LocationQuery,
): T[] | undefined => {
    const allowedLabels = new Set<string>()

    for (const [key, value] of Object.entries(query)) {
        if (!hasQueryValue(value)) {
            continue
        }

        const parsed = parseFilterKey(key)

        if (parsed?.field === "labels" && parsed.subKey && POSITIVE_OPERATIONS.has(parsed.operation)) {
            allowedLabels.add(parsed.subKey)
        }
    }

    return labels?.filter(label =>
        !hiddenPrefixes.some(prefix => label.key.startsWith(prefix)) ||
        allowedLabels.has(label.key),
    )
}
