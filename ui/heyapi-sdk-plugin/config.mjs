import {handler} from "./plugin.mjs";

/**
 * Type helper for `@kestra-io/sdk-plugin` plugin, returns {@link Plugin.Config} object
 * @param {typeof import("@hey-api/openapi-ts").definePluginConfig} definePluginConfig 
 */
export const defineKestraHeyConfig = (definePluginConfig, $) => {
    /** @type {import("./types").KestraSdkPlugin["Config"]} */
    const defaultConfig = {
        config: {
            output: "kestra-sdk",
            methodNameBuilder(operation) {
                return operation.operationId
            }
        },
        dependencies: ["@hey-api/typescript", "@hey-api/client-axios", "@hey-api/sdk"],
        handler: handler($),
        name: "ks-sdk",
    };
    return definePluginConfig(defaultConfig);
}