import {definePluginConfig} from "@hey-api/openapi-ts"
import {handler} from "./plugin"
import type {KestraSdkPlugin} from "./types"

/**
 * Strip a leading `get` verb from an operationId (`getExecution` -> `execution`,
 * `getFlowFromExecutionById` -> `flowFromExecutionById`), matching what the client-sdk
 * customizer's `normalizeGetOperationIds` does. This is done here — in the shared plugin — so every
 * consumer (OSS SDK, EE SDK, client-sdk) produces the same short, human-friendly method names. It is
 * a deliberate no-op for anything that is not a `get`+PascalCase id (e.g. `deleteFlow`,
 * `searchExecutions`, `listPlugins`). Temporary until the backend drops the `get` prefix itself.
 */
function stripGetPrefix(operationId: string): string {
    if (operationId.length > 3 && operationId.startsWith("get") && /^[A-Z]/.test(operationId.charAt(3))) {
        const remainder = operationId.slice(3)
        return remainder.charAt(0).toLowerCase() + remainder.slice(1)
    }
    return operationId
}

const defaultConfig: KestraSdkPlugin["Config"] = {
    config: {
        output: "kestra-sdk",
        methodNameBuilder(operation) {
            const operationId = stripGetPrefix(operation.operationId)
            // if its the "namespace" typescript reserved name, use
            // load as a prefix to avoid conflict
            if (operationId === "namespace") {
                return "loadNamespace"
            }
            if (["delete"].includes(operationId.replace(/_\d+$/, ""))) {
                const tag = operation.tags?.[0] ?? "default"
                const capitalizedTag = tag.charAt(0).toUpperCase()
                    + tag.slice(1).replace(/[_ ][a-zA-Z0-9]/g, (match: string) =>
                        match.charAt(1).toUpperCase(),
                    ).replace(/[^a-zA-Z0-9]/g, "")
                return operationId.replace(/_\d+$/, "") + capitalizedTag
            }
            return operationId
        },
    },
    dependencies: ["@hey-api/typescript", "@hey-api/client-fetch", "@hey-api/sdk"],
    handler,
    name: "ks-sdk",
}

export const defineConfigKestraHeyOptionalTenant = definePluginConfig(defaultConfig)