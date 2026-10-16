/** Maps a task edit section to the schema type its Source editor must declare (`task` or `trigger`). */
export function sectionSchemaType(section: string): string {
    return section.toLowerCase() === "triggers" ? "trigger" : "task"
}
