import type {Schema} from "../components/tasks/getTaskComponent"

export interface UnsetRequiredField {
    path: string
    label: string
}

export type PartialSchema = Omit<Partial<Schema>, "properties" | "allOf" | "anyOf" | "oneOf" | "items"> & {
    properties?: Record<string, PartialSchema>
    allOf?: PartialSchema[]
    anyOf?: PartialSchema[]
    oneOf?: PartialSchema[]
    items?: PartialSchema
}
type Definitions = Record<string, PartialSchema>

function resolveRef(schema: PartialSchema | undefined, definitions: Definitions): PartialSchema | undefined {
    if (!schema) return undefined
    if (schema.$ref) {
        return definitions[schema.$ref.split("/").pop() ?? ""]
    }
    return schema
}

function mergeAllOf(schema: PartialSchema, definitions: Definitions): PartialSchema {
    if (!schema.allOf?.length) return schema
    return schema.allOf.reduce<PartialSchema>((acc, branch) => {
        const resolved = resolveRef(branch, definitions) ?? branch
        return {
            ...acc,
            type: acc.type ?? resolved.type,
            properties: {...acc.properties, ...resolved.properties},
            required: [...new Set([...(acc.required ?? []), ...(resolved.required ?? [])])],
        }
    }, {...schema, allOf: undefined, properties: schema.properties ?? {}, required: schema.required ?? []})
}

function resolveAnyOfBranch(value: unknown, schema: PartialSchema, definitions: Definitions): PartialSchema | undefined {
    if (!schema.anyOf?.length) return undefined

    const resolvedBranches = schema.anyOf.map((branch) =>
        mergeAllOf(resolveRef(branch, definitions) ?? branch, definitions),
    )

    if (value && typeof value === "object" && !Array.isArray(value) && "type" in value) {
        const discriminator = (value as {type?: unknown}).type
        return resolvedBranches.find((branch) => branch.properties?.type?.const === discriminator)
    }

    const jsTypeToSchemaType: Record<string, string> = {
        string: "string",
        boolean: "boolean",
        number: "integer",
        array: "array",
        object: "object",
    }
    const jsType = Array.isArray(value) ? "array" : value === null || value === undefined ? undefined : typeof value
    if (!jsType) return undefined

    return resolvedBranches.find((branch) => branch.type === jsTypeToSchemaType[jsType])
}

function isUnset(value: unknown): boolean {
    return value === undefined || value === null || value === "" || (Array.isArray(value) && value.length === 0)
}

/**
 * Walks a task's data alongside its schema to find required properties left unset, recursing into
 * nested objects, array items and the anyOf branch matching the current value — independent of
 * whatever is currently mounted (drilled array rows and collapsed groups included).
 */
export function countUnsetRequiredFields(
    model: unknown,
    schema: PartialSchema | undefined,
    definitions: Definitions,
    path = "",
): UnsetRequiredField[] {
    if (!schema) return []
    const resolved = mergeAllOf(resolveRef(schema, definitions) ?? schema, definitions)

    if (resolved.anyOf?.length) {
        const branch = resolveAnyOfBranch(model, resolved, definitions)
        return branch ? countUnsetRequiredFields(model, branch, definitions, path) : []
    }

    if (Array.isArray(model) && resolved.items) {
        return model.flatMap((item, index) =>
            countUnsetRequiredFields(item, resolved.items, definitions, `${path}[${index}]`),
        )
    }

    // A map-shaped schema (additionalProperties, no fixed `properties`) is not walked: its keys
    // are runtime data, not schema, so there is no required list to check them against.
    if (!resolved.properties && !resolved.required?.length) return []

    const value = model && typeof model === "object" && !Array.isArray(model) ? model as Record<string, unknown> : {}
    const keys = new Set([...Object.keys(resolved.properties ?? {}), ...(resolved.required ?? [])])

    return [...keys].flatMap((key) => {
        const childSchema = resolved.properties?.[key]
        const childValue = value[key]
        const childPath = path ? `${path}.${key}` : key

        if (resolved.required?.includes(key) && isUnset(childValue)) {
            return [{path: childPath, label: key}]
        }

        return childValue !== undefined && childSchema
            ? countUnsetRequiredFields(childValue, childSchema, definitions, childPath)
            : []
    })
}
