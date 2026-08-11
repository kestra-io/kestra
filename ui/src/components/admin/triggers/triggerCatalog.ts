import {formatPluginTitle, getShortName} from "../../../utils/global"
import type {TriggerPluginDto} from "../../../stores/plugins"

export const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool"

export const isMcpTrigger = (trigger: Pick<TriggerPluginDto, "type">): boolean =>
    trigger.type === MCP_TOOL_TYPE || trigger.type.endsWith(".McpTool")

export const triggerDisplayName = (trigger: Pick<TriggerPluginDto, "type" | "name">): string => {
    if (trigger.name && trigger.name !== "Trigger") return trigger.name

    return formatPluginTitle(trigger.type.split(".").at(-2)) ?? getShortName(trigger.type)
}