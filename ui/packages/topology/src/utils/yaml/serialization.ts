// Plain JavaScript objects to and from YAML text, via js-yaml.

import {
    YAMLMap,
    isPair,
    type Pair,
} from "yaml"
import {dump, load} from "js-yaml"

export function parse<T = unknown>(item?: string, throwIfError = true): T | undefined {
    if (item === undefined) return undefined

    try {
        return load(item) as T
    } catch (e) {
        if (throwIfError) throw e
        return undefined
    }
}

const CRON_LINE_REGEX = /^(\s*-?\s*cron:\s*)([^\n#]*?)(\s*(#.*)?)$/gm

function preserveCronQuotes(yamlContent: string) {
    return yamlContent.replace(
        CRON_LINE_REGEX,
        (fullLine, prefix: string, valuePart: string, suffix: string) => {
            const value = valuePart.trim()
            const isEmptyValue = value === "" || value === "\"\"" || value === "''"

            if (isEmptyValue) {
                return `${prefix}${suffix ?? ""}`
            }

            const shouldSkip =
                value.startsWith("\"") ||
                value.startsWith("'") ||
                value.startsWith("|") ||
                value.startsWith(">")

            if (shouldSkip) {
                return fullLine
            }

            return `${prefix}"${value}"${suffix ?? ""}`
        },
    )
}

export function stringify(item: unknown) {
    if (item === undefined) return ""

    // transform() rebuilds every node and skips undefined values, so a shallow copy drops `deleted`
    const value = item === null || typeof item !== "object" || Array.isArray(item)
        ? item
        : {...item, deleted: undefined}

    const yamlContent = dump(transform(value), {
        lineWidth: -1,
        noCompatMode: true,
        quotingType: "\"",
    })

    return preserveCronQuotes(yamlContent)
}

const SORT_FIELDS = [
    "id",
    "type",
    "namespace",
    "description",
    "revision",
    "inputs",
    "variables",
    "tasks",
    "errors",
    "triggers",
    "listeners",
    "pluginDefaults",
]

export function sortPredicate(a: string, b: string) {
    const aIndex = SORT_FIELDS.indexOf(a)
    const bIndex = SORT_FIELDS.indexOf(b)
    const aIndexProtected = aIndex >= 0 ? aIndex : Number.MAX_SAFE_INTEGER
    const bIndexProtected = bIndex >= 0 ? bIndex : Number.MAX_SAFE_INTEGER

    return aIndexProtected - bIndexProtected
}

function sort(value: Record<string, unknown>) {
    return Object.keys(value)
        .sort(sortPredicate)
}

export function pairsToMap(pairs?: Pair[]) {
    const map = new YAMLMap()
    if (!isPair(pairs?.[0])) {
        return map
    }

    for (const pair of pairs!) {
        map.add(pair)
    };
    return map
}

function transform(value: unknown): unknown {
    if (value instanceof Array) {
        return value.map((r) => {
            return transform(r)
        })
    } else if (typeof value === "string" || value instanceof String) {
        return value
    } else if (value && typeof value === "object") {
        const record: Record<string, unknown> = value as Record<string, unknown>
        return sort(record).reduce((accumulator: Record<string, unknown>, r) => {
            if (record[r] !== undefined) {
                accumulator[r] = transform(record[r])
            }

            return accumulator
        }, Object.create({}))
    }

    return value
}
