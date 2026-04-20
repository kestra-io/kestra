import {formatPluginTitle, getShortName} from "../../utils/global";
import type {TriggerPluginDto} from "../../stores/plugins";

export const MCP_TOOL_TYPE = "io.kestra.core.models.triggers.McpTool";

export function isMcpTrigger(trigger: Pick<TriggerPluginDto, "type">): boolean {
    return trigger.type === MCP_TOOL_TYPE || trigger.type.endsWith(".McpTool");
}

export function triggerDisplayName(trigger: Pick<TriggerPluginDto, "type" | "name">): string {
    if (trigger.name && trigger.name !== "Trigger") return trigger.name;
    const segments = trigger.type.split(".");
    return formatPluginTitle(segments.at(-2)) ?? getShortName(trigger.type);
}
