import type {DefinePlugin} from "@hey-api/openapi-ts"

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
  /**
   * Absolute path to the raw OpenAPI spec file the SDK is generated from. When set, the plugin
   * stamps `export const OPENAPI_SPEC_HASH = sha256(specFile)[:16]` into the generated SDK, so the
   * committed SDK carries the hash of the spec it was built from (used by the dev-time staleness
   * check). Omit for consumers that don't need it (e.g. client-sdk, which regenerates on publish).
   */
  specPath?: string;
};

export type KestraSdkPlugin = DefinePlugin<UserConfig>;