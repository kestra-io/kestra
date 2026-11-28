const DEFINITIONS = "#/definitions/"
const SHARED = `${DEFINITIONS}shared-`
const SHARED_SUBTYPES = `${SHARED}subtypes`

type SchemaNode = Record<string, unknown>

const isNode = (value: unknown): value is SchemaNode => typeof value === "object" && value !== null && !Array.isArray(value)

function sharedRef(value: unknown, prefix = SHARED): string | undefined {
    if (!isNode(value)) return undefined
    const ref = value.$ref
    return typeof ref === "string" && ref.startsWith(prefix) && Object.keys(value).length === 1 ? ref : undefined
}

/** Undoes the backend's `JsonSchemaCompactor` in place, linking each shared schema by reference: treat the result as read-only. */
export function inflateCompactSchema<T extends {definitions?: Record<string, unknown>}>(schema: T): T {
    const definitions = schema.definitions
    if (!definitions) return schema

    const inflated = new Set<string>()

    function shared(ref: string): unknown {
        const key = ref.slice(DEFINITIONS.length)
        if (!inflated.has(key)) {
            inflated.add(key)
            definitions![key] = inflate(definitions![key])
        }
        const target = definitions![key]
        // A list a definition already held is shared through an alias of that definition.
        return isNode(target) && typeof target.$ref === "string" && Object.keys(target).length === 1
            ? definitions![target.$ref.slice(DEFINITIONS.length)]
            : target
    }

    function inflate(value: unknown): unknown {
        if (Array.isArray(value)) {
            value.forEach((child, index) => {
                value[index] = inflate(child)
            })
            return value
        }
        if (!isNode(value)) return value

        const ref = sharedRef(value)
        if (ref) return shared(ref)

        // `{...annotation, anyOf: [list]}` stands for one `allOf: [subtype, annotation]` branch per subtype of the list.
        const {anyOf, ...annotation} = value
        const listRef = Array.isArray(anyOf) && anyOf.length === 1 ? sharedRef(anyOf[0], SHARED_SUBTYPES) : undefined
        if (listRef && Object.keys(annotation).length > 0) {
            const subtypes = (shared(listRef) as {anyOf: unknown[]}).anyOf
            for (const key of Object.keys(annotation)) {
                annotation[key] = inflate(annotation[key])
                delete value[key]
            }
            value.anyOf = subtypes.map(subtype => ({allOf: [subtype, annotation]}))
            return value
        }

        for (const key of Object.keys(value)) {
            value[key] = inflate(value[key])
        }
        return value
    }

    for (const key of Object.keys(definitions)) {
        if (!key.startsWith("shared-")) {
            definitions[key] = inflate(definitions[key])
        }
    }
    for (const key of Object.keys(schema)) {
        if (key !== "definitions") {
            (schema as SchemaNode)[key] = inflate((schema as SchemaNode)[key])
        }
    }
    for (const key of Object.keys(definitions)) {
        if (key.startsWith("shared-")) {
            delete definitions[key]
        }
    }
    return schema
}
