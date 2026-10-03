import type {LocationQuery} from "vue-router"
import {parseFilterKey} from "@kestra-io/design-system"

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

        if (parsed?.field === "labels" && parsed.subKey) {
            allowedLabels.add(parsed.subKey)
        }
    }

    return labels?.filter(label =>
        !hiddenPrefixes.some(prefix => label.key.startsWith(prefix)) ||
        allowedLabels.has(label.key),
    )
}
