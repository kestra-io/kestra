import {formatPluginTitle, getShortName} from "../../../utils/global"
import type {TriggerPluginDto} from "../../../stores/plugins"

export const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool"

export const isMcpTrigger = (trigger: Pick<TriggerPluginDto, "type">): boolean =>
    trigger.type === MCP_TOOL_TYPE || trigger.type.endsWith(".McpTool")

export const triggerDisplayName = (trigger: Pick<TriggerPluginDto, "type" | "name">): string => {
    // Backend now provides descriptive names with plugin context (e.g., "Kafka Realtime", "AWS SQS")
    if (trigger.name && trigger.name !== "Trigger" && trigger.name !== "RealtimeTrigger") {
        return trigger.name
    }

    // Fallback for any edge cases - extract from type
    return formatPluginTitle(trigger.type.split(".").at(-2)) ?? getShortName(trigger.type)
}