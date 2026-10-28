import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"

const TASK_SECTIONS = ["tasks", "errors", "finally", "afterExecution"]

export type DefinitionCheck = {error?: "yaml" | "identity"}

export function extractTaskDefinition(source: string | undefined, taskId: string): string | undefined {
    if (!source) return undefined
    for (const section of TASK_SECTIONS) {
        const block = flowYamlUtils.extractBlock({source, section, key: taskId})
        if (block) return block
    }
    return undefined
}

/** The server checks the whole flow again; this only catches what the editor can show right away. */
export function checkDefinition(original: string, edited: string): DefinitionCheck {
    let parsed: Record<string, unknown> | undefined
    try {
        parsed = flowYamlUtils.parse<Record<string, unknown>>(edited)
    } catch {
        return {error: "yaml"}
    }
    const before = flowYamlUtils.parse<Record<string, unknown>>(original)
    if (!parsed || parsed.id !== before?.id || parsed.type !== before?.type) {
        return {error: "identity"}
    }
    return {}
}
