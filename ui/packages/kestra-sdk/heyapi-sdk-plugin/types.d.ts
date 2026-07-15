import type {DefinePlugin} from "@hey-api/openapi-ts";

export type UserConfig = {
  /**
   * Plugin name. Must be unique.
   */
  name: "ks-sdk";
  /**
   * Name of the generated file.
   *
   * @default 'ks-sdk'
   */
  output?: string;
  /**
   * Function to build method names from operations.
   * Receives the operation object and must return a string or undefined to skip the operation.
   */
  methodNameBuilder?: (operation: any) => string; 
};

export type KestraSdkPlugin = DefinePlugin<UserConfig>;