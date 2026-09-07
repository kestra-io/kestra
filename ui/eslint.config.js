import pluginVue from "eslint-plugin-vue"
import tsParser from "@typescript-eslint/parser"
import {defineConfig, globalIgnores} from "eslint/config"
import kestraTokens from "./scripts/tokens/eslintPlugin.mjs"

export default defineConfig([
    globalIgnores(["**/node_modules/*", "node/*", "playwright-report/*", "test-results/*", "coverage/*", "**/dist/*", "packages/kestra-sdk/src/openapi/*"]),
    ...pluginVue.configs["flat/base"],
    // Tooling (plugins/, scripts/, lint-rules/, *.config.js) stays JS on purpose; the app, test and storybook trees do not.
    {
        files: [
            "src/**/*.{js,jsx,mjs,cjs}",
            "tests/**/*.{js,jsx,mjs,cjs}",
            ".storybook/**/*.{js,jsx,mjs,cjs}",
            "packages/*/src/**/*.{js,jsx,mjs,cjs}",
            "packages/*/tests/**/*.{js,jsx,mjs,cjs}",
            "packages/*/.storybook/**/*.{js,jsx,mjs,cjs}",
        ],
        languageOptions: {parser: tsParser, parserOptions: {ecmaFeatures: {jsx: true}}},
        linterOptions: {noInlineConfig: true},
        rules: {
            "no-restricted-syntax": ["error", {
                selector: "Program",
                message: "Write this as TypeScript: JavaScript files are not allowed in the app, test or storybook trees.",
            }],
        },
    },
    // `<style>` blocks are stylelint's half of this; here it is the tokens written in JavaScript.
    {
        files: ["**/*.{js,mjs,cjs,ts,vue}"],
        plugins: {"kestra-tokens": kestraTokens},
        rules: {"kestra-tokens/no-undeclared-ks-token": "error"},
    },
    // Formatting rules for JS/TS files (not .vue — handled below by vue/* variants)
    {
        files: ["**/*.{js,mjs,cjs,ts}"],
        languageOptions: {parser: tsParser},
        rules: {
            quotes: ["warn", "double"],
            semi: ["warn", "never"],
            "comma-dangle": ["warn", "always-multiline"],
            "object-curly-spacing": ["warn", "never"],
            "array-bracket-spacing": ["warn", "never"],
        },
    },
    {
        files: ["**/*.vue"],
        languageOptions: {parserOptions: {
            parser: tsParser,
            extraFileExtensions: [".vue"],
        }},
        rules: {
            // Formatting — vue/* variants handle indentation inside SFCs;
            // base indent rule must be off to avoid double-reporting
            indent: "off",
            "vue/html-indent": ["warn", 4, {baseIndent: 1}],
            "vue/script-indent": ["warn", 4, {baseIndent: 1}],
            quotes: ["warn", "double"],
            semi: ["warn", "never"],
            "comma-dangle": ["warn", "always-multiline"],
            "object-curly-spacing": ["warn", "never"],
            "array-bracket-spacing": ["warn", "never"],
            "vue/object-curly-spacing": ["warn", "never"],
            // Semantic rules
            "vue/block-lang": ["error", {"script": {"lang": "ts"}}],
            "vue/component-api-style": ["error", ["script-setup"]],
            "vue/no-mutating-props": "error",
            "vue/this-in-template": "error",
            "vue/block-order": ["error", {order: ["template", "script", "style"]}],
            "vue/enforce-style-attribute": ["warn", {"allow": ["scoped"]}],
            "vue/component-name-in-template-casing": ["error", "PascalCase", {"registeredComponentsOnly": true}],
            "vue/attribute-hyphenation": ["error", "never"],
        },
    },
    {
        // The design system intentionally ships unscoped overrides for
        // element-plus (kel-*) classes so every consumer gets the same look.
        // The rule still applies everywhere else, where unscoped styles
        // would leak globally by accident.
        files: ["packages/design-system/src/components/**/*.vue"],
        rules: {
            "vue/enforce-style-attribute": "off",
        },
    },

])
