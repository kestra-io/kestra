import {flowYamlUtils} from "@kestra-io/topology"
import {storageKeys} from "./constants"

export type FlowTemplateErrorCode = "invalid_mapping" | "parse_error"

export interface FlowTemplateValidation {
    errorCode?: FlowTemplateErrorCode
    parseMessage?: string
}

/** Template used when neither the user nor the instance has configured one. */
export function builtInFlowTemplate(id: string, namespace: string): string {
    return `
id: ${id}
namespace: ${namespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`.trim()
}

/** Template saved by this user from the settings page, `undefined` when none is set. */
export function userFlowTemplate(): string | undefined {
    const stored = localStorage.getItem(storageKeys.FLOW_TEMPLATE)
    return typeof stored === "string" && stored.trim() ? stored.trim() : undefined
}

/**
 * Placeholder opened in the editor for a new flow.
 *
 * The template saved by the user is preferred over the instance-wide `kestra.flowTemplate`
 * configuration, which is itself preferred over the built-in one. `id` and `namespace` may be
 * left out of any configured template: each of them is generated afterwards when the parsed
 * flow does not set it.
 */
export function resolveFlowTemplate(id: string, namespace: string, instanceTemplate?: string): string {
    const instance = typeof instanceTemplate === "string" && instanceTemplate.trim()
        ? instanceTemplate.trim()
        : undefined

    return userFlowTemplate() ?? instance ?? builtInFlowTemplate(id, namespace)
}

/** An empty template is valid: it clears the setting so the instance or built-in one is used. */
export function validateFlowTemplate(template: string): FlowTemplateValidation {
    if (!template.trim()) {
        return {}
    }

    let parsed: unknown
    try {
        parsed = flowYamlUtils.parse(template)
    } catch (e: unknown) {
        return {errorCode: "parse_error", parseMessage: e instanceof Error ? e.message : String(e)}
    }

    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
        return {errorCode: "invalid_mapping"}
    }

    return {}
}
