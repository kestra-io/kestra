import {definePluginConfig} from "@hey-api/openapi-ts";

import {handler} from "./plugin";
import type {KestraSdkPlugin} from "./types";

export const defaultConfig: KestraSdkPlugin["Config"] = {
  config: {
    output: "kestra-sdk",
  },
  dependencies: ["@hey-api/typescript", "@hey-api/client-axios", "@hey-api/sdk"],
  handler,
  name: "@kestra-io/sdk-plugin",
};

/**
 * Type helper for `my-plugin` plugin, returns {@link Plugin.Config} object
 */
export const defineKestraHeyConfig = definePluginConfig(defaultConfig);