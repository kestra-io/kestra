import {getType} from "./getTaskComponent"
import {resolve$ref} from "../../../../utils/utils"

type FieldSchema = Record<string, unknown>

const OBJECT_LIKE_TYPES = new Set(["object", "complex"])

function branchesOf(schema: FieldSchema): FieldSchema[] {
    return (schema.anyOf as FieldSchema[] | undefined)
        ?? (schema.oneOf as FieldSchema[] | undefined)
        ?? []
}

export function looksLikeObject(
    schema: FieldSchema,
    definitions: Record<string, FieldSchema>,
    key?: string,
): boolean {
    if (!schema) return false

    const type = getType(schema, definitions, key)

    if (OBJECT_LIKE_TYPES.has(type)) return true

    if (type === "any-of") {
        return branchesOf(schema).some((branch) =>
            looksLikeObject(
                resolve$ref({definitions}, branch),
                definitions,
            ),
        )
    }

    return false
}

function resolvedProperties(
    schema: FieldSchema,
    definitions: Record<string, FieldSchema>,
): Record<string, FieldSchema> {
    const resolved = resolve$ref({definitions}, schema) as FieldSchema | undefined

    if (!resolved) return {}

    const properties = resolved.properties as
        | Record<string, FieldSchema>
        | undefined

    if (properties) return properties

    const allOf = resolved.allOf as FieldSchema[] | undefined

    return (allOf ?? []).reduce(
        (acc: Record<string, FieldSchema>, item: FieldSchema) => ({
            ...acc,
            ...(
                resolve$ref({definitions}, item) as FieldSchema | undefined
            )?.properties as Record<string, FieldSchema> | undefined,
        }),
        {},
    )
}

export function shouldDrillItem(
    schema: FieldSchema,
    definitions: Record<string, FieldSchema>,
    key?: string,
): boolean {
    if (!schema) return false

    const resolved = resolve$ref({definitions}, schema) as
        | FieldSchema
        | undefined

    const branches = branchesOf(schema).length
        ? branchesOf(schema)
        : resolved
            ? branchesOf(resolved)
            : []

    if (branches.length) {
        return branches.some((branch) =>
            looksLikeObject(
                resolve$ref({definitions}, branch),
                definitions,
            ),
        )
    }

    if (!looksLikeObject(schema, definitions, key)) return false

    return Object.values(
        resolvedProperties(schema, definitions),
    ).some((prop: FieldSchema) => {
        const type = getType(prop, definitions)

        if (OBJECT_LIKE_TYPES.has(type)) return true

        if (type === "list" || type === "array") {
            const items = prop.items as FieldSchema | undefined

            return items
                ? looksLikeObject(items, definitions)
                : false
        }

        if (type === "any-of") {
            return branchesOf(prop).some((branch) =>
                looksLikeObject(
                    resolve$ref({definitions}, branch),
                    definitions,
                ),
            )
        }

        return false
    })
}

export function describeArrayItem(
    element: unknown,
    index: number,
): string {
    if (
        element &&
        typeof element === "object" &&
        !Array.isArray(element)
    ) {
        const record = element as Record<string, unknown>

        return String(
            record.id ??
            record.name ??
            record.type ??
            `#${index + 1}`,
        )
    }

    return `#${index + 1}`
}

export type ValueSummary =
    | {kind: "empty"}
    | {kind: "count"; count: number}
    | {kind: "text"; text: string};

const MAX_TEXT = 48
const MAX_INLINE_ITEMS = 3

function truncate(text: string): string {
    return text.length > MAX_TEXT
        ? `${text.slice(0, MAX_TEXT - 1)}…`
        : text
}

function afterLastDot(value: string): string {
    const index = value.lastIndexOf(".")

    return index >= 0
        ? value.slice(index + 1)
        : value
}

function isEmpty(value: unknown): boolean {
    if (value === null || value === undefined) return true
    if (Array.isArray(value)) return value.length === 0
    if (typeof value === "object") return Object.keys(value).length === 0
    if (typeof value === "string") return value.trim() === ""

    return false
}

function isScalar(value: unknown): boolean {
    return (
        value === null ||
        value === undefined ||
        typeof value !== "object"
    )
}

function scalarText(value: unknown): string {
    if (value === null || value === undefined) return ""

    if (Array.isArray(value)) {
        return value.every(isScalar)
            ? value.map(String).join(", ")
            : `${value.length} items`
    }

    if (typeof value === "object") return "…"

    return String(value)
}

export function summarizeValue(value: unknown): ValueSummary {
    if (isEmpty(value)) return {kind: "empty"}

    if (Array.isArray(value)) {
        if (
            value.every(isScalar) &&
            value.length <= MAX_INLINE_ITEMS
        ) {
            return {
                kind: "text",
                text: truncate(value.map(String).join(", ")),
            }
        }

        return {
            kind: "count",
            count: value.length,
        }
    }

    if (typeof value === "object" && value !== null) {
        const record = value as Record<string, unknown>
        const discriminator = record.type ?? record.$type

        const entries = Object.entries(record).filter(
            ([key]) =>
                key !== "type" &&
                key !== "$type",
        )

        if (discriminator) {
            const first = entries[0]
            const tail = first
                ? ` · ${first[0]}: ${scalarText(first[1])}`
                : ""

            return {
                kind: "text",
                text: truncate(
                    `${afterLastDot(
                        String(discriminator),
                    )}${tail}`,
                ),
            }
        }

        const pairs = entries
            .slice(0, MAX_INLINE_ITEMS)
            .map(
                ([key, entryValue]) =>
                    `${key}=${scalarText(entryValue)}`,
            )
            .join(", ")

        return {
            kind: "text",
            text: truncate(pairs),
        }
    }

    return {
        kind: "text",
        text: truncate(String(value)),
    }
}