import {definePluginConfig} from "@hey-api/openapi-ts";
import {handler} from "./plugin";
import type {KestraSdkPlugin} from "./types";

const defaultConfig: KestraSdkPlugin["Config"] = {
  config: {
    output: "kestra-sdk",
    methodNameBuilder(operation) {
        return operation.operationId
    }
  },
  dependencies: ["@hey-api/typescript", "@hey-api/client-axios", "@hey-api/sdk"],
  handler,
  name: "ks-sdk",
};

/**
 * Type helper for `@kestra-io/sdk-plugin` plugin, returns {@link Plugin.Config} object
 */
export const defineKestraHeyConfig = definePluginConfig(defaultConfig);