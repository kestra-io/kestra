import {getShortName} from "../../../utils/global"
import type {TriggerPluginDto} from "../../../stores/plugins"

export const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool"

export const isMcpTrigger = (trigger: Pick<TriggerPluginDto, "type">): boolean =>
    trigger.type === MCP_TOOL_TYPE || trigger.type.endsWith(".McpTool")

type NamedTrigger = Pick<TriggerPluginDto, "type" | "name" | "pluginTitle" | "pluginGroupTitle">;

const GENERIC_REALTIME = /^realtimetrigger$/i

const contextualize = (trigger: NamedTrigger, context: string | undefined): string => {
    const name = trigger.name || getShortName(trigger.type)
    if (!context) return name
    if (GENERIC_REALTIME.test(name)) return `${context} Realtime`
    // `MailReceivedTrigger` -> `Mail Received`: a class name is CamelCase, a card label is not.
    const stripped = name.replace(/trigger$/i, "").replace(/([a-z\d])([A-Z])/g, "$1 $2")
    return stripped ? `${context} ${stripped}` : context
}

// Plugins conventionally name their trigger classes `Trigger`, `RealtimeTrigger`,
// `CommandsTrigger`, ... so the raw class name renders walls of identical cards. Labels are
// built against the whole catalog: a name that is unique stays as is, a colliding one gets the
// plugin's own declared, correctly-cased title (never a package-derived guess: two unrelated
// plugins can share a package segment, e.g. io.kestra.plugin.mongodb vs
// io.kestra.plugin.debezium.mongodb). When that title still collides, the label escalates to
// the owning plugin artifact's title alone ("NATS", "Datagen").
export const buildTriggerDisplayNames = (triggers: NamedTrigger[]): Map<string, string> => {
    const labels = new Map<string, string>(triggers.map(trigger => {
        const name = trigger.name || getShortName(trigger.type)
        const isGeneric = GENERIC_REALTIME.test(name) || name === "Trigger"
        return [trigger.type, isGeneric ? contextualize(trigger, trigger.pluginTitle) : name]
    }))

    for (const context of ["pluginTitle", "pluginGroupTitle"] as const) {
        const counts = new Map<string, number>()
        labels.forEach(label => counts.set(label, (counts.get(label) ?? 0) + 1))
        triggers
            .filter(trigger => (counts.get(labels.get(trigger.type)!) ?? 0) > 1 && trigger[context])
            .forEach(trigger => labels.set(trigger.type, contextualize(trigger, trigger[context])))
    }

    return labels
}
