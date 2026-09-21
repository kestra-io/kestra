import {TUTORIAL_NAMESPACE} from "../constants"

export function isExampleFlow(namespace: string | undefined): boolean {
    return namespace === TUTORIAL_NAMESPACE
}

/**
 * Where in the UI a run was started, derived from the matched route name rather than the URL so it
 * survives tenant prefixes and path changes. Unknown routes fall back to their first segment.
 */
export function executionLocation(routeName: string | undefined, kind: "NORMAL" | "PLAYGROUND"): string | undefined {
    if (kind === "PLAYGROUND") return "playground"
    if (!routeName) return undefined

    if (routeName === "flows/create" || routeName.startsWith("flows/update")) return "flow_editor"
    if (routeName === "flows/list" || routeName === "flows/search") return "flow_list"
    if (routeName.startsWith("executions/")) return "executions_view"

    return routeName.split("/")[0] || undefined
}

/**
 * Navbar section of a page view. Every route in both editions is named `section/subpage`, so the
 * first segment is the section - no lookup table to keep in sync.
 */
export function routeSection(routeName: string | undefined): string | undefined {
    return routeName?.split("/")[0] || undefined
}

type TaskLike = {type?: string; tasks?: TaskLike[] | null}

/**
 * Flow shape at creation: how many tasks it holds and how many distinct plugins it wires together.
 * Walks `tasks` nesting (Sequential/Parallel); branch containers (If/Switch, errors) are not walked,
 * so both counts are lower bounds.
 */
export function flowTaskStats(tasks: TaskLike[] | undefined): {taskCount: number; pluginCount: number} {
    const types = new Set<string>()
    let taskCount = 0

    const walk = (list: TaskLike[] | undefined | null) => {
        for (const task of list ?? []) {
            taskCount++
            if (task.type) types.add(task.type)
            walk(task.tasks)
        }
    }
    walk(tasks)

    return {taskCount, pluginCount: types.size}
}

// Keyed on the full type, not the short class name: 78 of the ~140 plugin triggers are literally
// named `Trigger`, so short names collide into one meaningless bucket.
const TRIGGER_KINDS: Record<string, string> = {
    "io.kestra.plugin.core.trigger.Schedule": "cron",
    "io.kestra.plugin.core.trigger.ScheduleOnDates": "cron",
    "io.kestra.plugin.core.trigger.Webhook": "webhook",
    "io.kestra.plugin.core.trigger.Flow": "flow",
}

/**
 * How the flow is meant to be started: `manual` with no trigger, otherwise the first trigger mapped
 * to the funnel vocabulary. Everything else — plugin triggers included — is bucketed as `other` to
 * keep this a curated dimension; per-plugin usage is not what this property is for.
 */
export function primaryTriggerType(triggers: {type?: string}[] | undefined): string {
    const first = triggers?.[0]?.type
    if (!first) return "manual"

    return TRIGGER_KINDS[first] ?? "other"
}
