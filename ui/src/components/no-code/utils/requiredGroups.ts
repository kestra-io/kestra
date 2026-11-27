import type {Schema} from "../components/tasks/getTaskComponent"

type GroupSchema = Pick<Schema, "$group" | "$requiredGroups"> & {
    allOf?: GroupSchema[]
    anyOf?: GroupSchema[]
    properties?: Record<string, GroupSchema>
}

export function isUnset(value: unknown): boolean {
    return value === undefined || value === null || value === "" || (Array.isArray(value) && value.length === 0)
}

export function schemaGroup(schema: GroupSchema | undefined): string | null {
    for (const branch of [...(schema?.allOf ?? []), ...(schema?.anyOf ?? [])]) {
        const group = schemaGroup(branch)
        if (group) return group
    }
    return schema?.$group ?? null
}

export function unsetRequiredGroups(model: unknown, schema: GroupSchema): Map<string, string[]> {
    const value = model && typeof model === "object" && !Array.isArray(model) ? model as Record<string, unknown> : {}
    const entries = Object.entries(schema.properties ?? {})
    return new Map((schema.$requiredGroups ?? []).flatMap((group) => {
        const keys = entries.filter(([, child]) => schemaGroup(child) === group).map(([key]) => key)
        return keys.length && keys.every((key) => isUnset(value[key])) ? [[group, keys] as const] : []
    }))
}
