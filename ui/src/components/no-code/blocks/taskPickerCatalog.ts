import {isEntryAPluginElementPredicate, type PluginElement} from "../../../utils/pluginUtils"
import type {BlockSection} from "../../../utils/flowableBlockOps"

export interface PickerEntry {
    fqcn: string
    name: string
    label: string
    group: string
}

export interface PickerAppGroup {
    group: string
    count: number
    sampleFqcn: string
}

export type PickerEntryKind = "tasks" | "triggers"

export const PICKER_MAX_RESULTS = 50

export const RECENT_FQCNS_KEY = "blockEditor.recentTaskTypes"

const RECENT_FQCNS_LIMIT = 8

export const SUGGESTED_FQCNS_BY_SECTION: Record<BlockSection, string[]> = {
    tasks: [
        "io.kestra.plugin.core.log.Log",
        "io.kestra.plugin.core.http.Request",
        "io.kestra.plugin.scripts.python.Script",
        "io.kestra.plugin.scripts.shell.Commands",
        "io.kestra.plugin.core.flow.Subflow",
        "io.kestra.plugin.core.flow.If",
        "io.kestra.plugin.core.flow.Switch",
        "io.kestra.plugin.core.flow.Loop",
        "io.kestra.plugin.core.flow.Parallel",
        "io.kestra.plugin.core.flow.Dag",
    ],
    triggers: [
        "io.kestra.plugin.core.trigger.Schedule",
        "io.kestra.plugin.core.trigger.Webhook",
        "io.kestra.plugin.core.trigger.Flow",
    ],
    errors: [
        "io.kestra.plugin.core.log.Log",
        "io.kestra.plugin.core.execution.Fail",
        "io.kestra.plugin.core.http.Request",
    ],
    finally: [
        "io.kestra.plugin.core.log.Log",
        "io.kestra.plugin.core.storage.PurgeCurrentExecutionFiles",
        "io.kestra.plugin.core.http.Request",
    ],
    afterExecution: [
        "io.kestra.plugin.core.log.Log",
        "io.kestra.plugin.core.http.Request",
    ],
}

export function buildPickerEntries(
    plugins: Record<string, unknown>[] | undefined,
    kind: PickerEntryKind,
): PickerEntry[] {
    if (!plugins) return []
    const entries: PickerEntry[] = []
    const seen = new Set<string>()
    for (const plugin of plugins) {
        const value = plugin[kind]
        if (!isEntryAPluginElementPredicate(kind, value)) continue
        for (const el of value as PluginElement[]) {
            if (el.deprecated || seen.has(el.cls)) continue
            seen.add(el.cls)
            const parts = el.cls.split(".")
            entries.push({
                fqcn: el.cls,
                name: parts[parts.length - 1] ?? el.cls,
                label: el.title ?? parts[parts.length - 1] ?? el.cls,
                group: (plugin.title as string) ?? (plugin.name as string) ?? "",
            })
        }
    }
    return entries
}

export function filterPickerEntries(entries: PickerEntry[], search: string): PickerEntry[] {
    const needle = search.trim().toLowerCase()
    if (!needle) return entries
    return entries.filter(
        entry =>
            entry.label.toLowerCase().includes(needle) ||
            entry.fqcn.toLowerCase().includes(needle) ||
            entry.group.toLowerCase().includes(needle),
    )
}

export function groupPickerEntriesByApp(entries: PickerEntry[]): PickerAppGroup[] {
    const groups = new Map<string, PickerAppGroup>()
    for (const entry of entries) {
        const existing = groups.get(entry.group)
        if (existing) existing.count++
        else groups.set(entry.group, {group: entry.group, count: 1, sampleFqcn: entry.fqcn})
    }
    return [...groups.values()].sort((a, b) => b.count - a.count)
}

export function loadRecentFqcns(): string[] {
    try {
        const raw = localStorage.getItem(RECENT_FQCNS_KEY)
        return raw ? JSON.parse(raw) : []
    } catch {
        return []
    }
}

export function pushRecentFqcn(fqcn: string, current: string[]): string[] {
    const next = [fqcn, ...current.filter(f => f !== fqcn)].slice(0, RECENT_FQCNS_LIMIT)
    try {
        localStorage.setItem(RECENT_FQCNS_KEY, JSON.stringify(next))
    } catch {
    }
    return next
}
