import {getShortName} from "../../../utils/global"
import type {TriggerPluginDto} from "../../../stores/plugins"

export const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool"

export const isMcpTrigger = (trigger: Pick<TriggerPluginDto, "type">): boolean =>
    trigger.type === MCP_TOOL_TYPE || trigger.type.endsWith(".McpTool")

export const triggerDisplayName = (trigger: Pick<TriggerPluginDto, "type" | "name" | "pluginTitle">): string => {
    // Plugins conventionally name their realtime trigger class `RealtimeTrigger`, so dozens of
    // cards would share that exact label; keep the `Realtime` suffix so the card also stays
    // distinguishable from the same plugin's polling `Trigger` card.
    if (trigger.name === "RealtimeTrigger" && trigger.pluginTitle) return `${trigger.pluginTitle} Realtime`

    if (trigger.name && trigger.name !== "Trigger") return trigger.name

    // Most plugins name their trigger class `Trigger`, so `trigger.name` above is useless for
    // disambiguation. Fall back to the plugin's own declared, correctly-cased title (resolved
    // server-side from its metadata) rather than guessing from the class package: two unrelated
    // plugins can share a package segment (e.g. io.kestra.plugin.mongodb vs
    // io.kestra.plugin.debezium.mongodb both end in "mongodb"), and a package-derived guess would
    // collide the two under the same wrongly-cased "Mongodb" label.
    return trigger.pluginTitle || getShortName(trigger.type)
}