/**
 * Maps a task edit section (`tasks`, `errors`, `finally`, `afterExecution`, `triggers`) to the schema type its Source
 * editor must declare. Monaco names the model `<schemaType>-<path>.yaml` and `yamlSchemas` binds a schema by that name,
 * so passing the lower-cased section as is (`tasks-…`) matches neither `task-*` nor `trigger-*` and leaves the editor
 * without suggestions.
 */
export function sectionSchemaType(section: string): string {
    const normalized = section.toLowerCase()

    if (normalized === "triggers") {
        return "trigger"
    }

    return ["tasks", "errors", "finally", "afterexecution"].includes(normalized) ? "task" : normalized
}
