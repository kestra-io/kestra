// Plain JavaScript objects to and from YAML text, via js-yaml.

import {CORE_SCHEMA, DUMP_SCHEMA, YAMLException, dump, loadAll, mergeTag} from "js-yaml"

// Merge keys as js-yaml 4 did; unquoted dates stay strings so a flow round-trips unchanged
const LOAD_SCHEMA = CORE_SCHEMA.withTags(mergeTag)

export function parse<T = unknown>(item?: string, throwIfError = true): T | undefined {
    if (item === undefined) return undefined

    try {
        // js-yaml 5 `load` throws on a stream with no document, where 4 returned undefined
        const documents = loadAll(item, {schema: LOAD_SCHEMA})
        if (documents.length > 1) throw new YAMLException("expected a single document in the stream, but found more")
        return documents[0] as T
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
        schema: DUMP_SCHEMA,
        quoteStyle: "double",
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

function transform(value: unknown): unknown {
    if (value instanceof Array) {
        return value.map((r) => {
            return transform(r)
        })
    } else if (typeof value === "string" || value instanceof String || value instanceof Date) {
        return value
    } else if (value instanceof Object) {
        const record = value as Record<string, unknown>
        return sort(record).reduce<Record<string, unknown>>((accumulator, r) => {
            if (record[r] !== undefined) {
                accumulator[r] = transform(record[r])
            }

            return accumulator
        }, {})
    }

    return value
}
