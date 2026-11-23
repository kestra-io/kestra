// Types for `eslintPlugin.mjs`, loaded by eslint.config.js as plain JavaScript.
import type {ESLint} from "eslint";

declare const plugin: ESLint.Plugin & {rules: Required<ESLint.Plugin>["rules"]};
export default plugin;
