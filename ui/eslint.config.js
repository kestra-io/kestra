import pluginVue from "eslint-plugin-vue";
import tsParser from "@typescript-eslint/parser";
import {defineConfig, globalIgnores} from "eslint/config";

const components = (folder) => `src/components/${folder}/**/*.vue`;

export default defineConfig([
    globalIgnores(["**/node_modules/*", "node/*", "playwright-report/*", "test-results/*", "coverage/*"]),
    ...pluginVue.configs["flat/base"],
    {
        files: ["**/*.vue"],
        languageOptions: {parserOptions: {
            parser: tsParser,
            extraFileExtensions: [".vue"],
        }},
        rules: {
            "vue/block-lang": ["warn", {"script": {"lang": "ts"}}],
            "vue/this-in-template": "error",
            "vue/block-order": ["error", {order: ["template", "script", "style"]}],
            "vue/enforce-style-attribute": ["warn", {"allow": ["scoped"]}],
            "vue/component-name-in-template-casing": ["error", "PascalCase", {"registeredComponentsOnly": true}],
            "vue/attribute-hyphenation": ["error", "never"],
        },
    },
    {
        files: [components("filter"), components("code")],
        ignores: [components("code/components/tasks")],
        rules: {"vue/component-api-style": ["error", ["script-setup"]]},
    },
]);
