import type {DefinePlugin} from "@hey-api/openapi-ts";

export type UserConfig = {
  /**
   * Plugin name. Must be unique.
   */
  name: "@kestra-io/sdk-plugin";
  /**
   * Name of the generated file.
   *
   * @default '@kestra-io/sdk-plugin'
   */
  output?: string;
};

export type KestraSdkPlugin = DefinePlugin<UserConfig>;