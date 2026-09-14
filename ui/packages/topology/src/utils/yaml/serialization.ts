// Plain JavaScript objects to and from YAML text, via js-yaml.

import {
    YAMLMap,
    isPair,
} from "yaml"
import {dump, load} from "js-yaml"
import cloneDeep from "lodash/cloneDeep"

export function parse<T = any>(item?: string, throwIfError = true): T | undefined {
    if (item === undefined) return undefined

    try {
        return load(item) as any
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

export function stringify(item: any) {
    if (item === undefined) return ""

    const clonedValue = cloneDeep(item)
    delete clonedValue.deleted

    const yamlContent = dump(transform(clonedValue), {
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

function sort(value: Record<string, any>) {
    return Object.keys(value)
        .sort(sortPredicate)
}

export function pairsToMap(pairs?: any[]) {
    const map = new YAMLMap()
    if (!isPair(pairs?.[0])) {
        return map
    }

    for (const pair of pairs!) {
        map.add(pair)
    };
    return map
}

function transform(value: any): any {
    if (value instanceof Array) {
        return value.map((r) => {
            return transform(r)
        })
    } else if (typeof value === "string" || value instanceof String) {
        return value
    } else if (value instanceof Object) {
        return sort(value).reduce((accumulator, r) => {
            if (value[r] !== undefined) {
                accumulator[r] = transform(value[r])
            }

            return accumulator
        }, Object.create({}))
    }

    return value
}
