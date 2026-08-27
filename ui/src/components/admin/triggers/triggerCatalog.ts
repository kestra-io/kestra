import {getShortName} from "../../../utils/global"
import type {TriggerPluginDto} from "../../../stores/plugins"

export const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool"

export const isMcpTrigger = (trigger: Pick<TriggerPluginDto, "type">): boolean =>
    trigger.type === MCP_TOOL_TYPE || trigger.type.endsWith(".McpTool")

type TriggerNaming = Pick<TriggerPluginDto, "type" | "name" | "pluginTitle" | "group">

// "MailReceivedTrigger" -> "Mail Received", "RealtimeTrigger" -> "Realtime", bare "Trigger" -> ""
const classKind = (trigger: TriggerNaming): string =>
    (trigger.name || getShortName(trigger.type))
        .replace(/Trigger$/, "")
        .replace(/([a-z\d])([A-Z])/g, "$1 $2")

/**
 * The plugin's own declared title (resolved server-side, see Plugin#titleFor) plus whatever its
 * class name adds, since a plugin usually ships several triggers and Kestra's convention names most
 * of their classes a bare `Trigger`: "Debezium MongoDB" and "Debezium MongoDB Realtime" rather than
 * two cards both reading "Mongodb". Class names are unique within a plugin's sub-group, so this
 * needs no suffix of its own to keep two cards apart.
 */
export const triggerDisplayName = (trigger: TriggerNaming): string => {
    // A core trigger's class name is already unique and readable ("Schedule", "Webhook"), and its
    // plugin title only restates the category the card already shows.
    if (trigger.group === "core" || !trigger.pluginTitle) {
        return classKind(trigger) || getShortName(trigger.type)
    }

    return `${trigger.pluginTitle} ${classKind(trigger)}`.trim()
}
