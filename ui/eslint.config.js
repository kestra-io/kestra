import pluginVue from "eslint-plugin-vue"
import tsParser from "@typescript-eslint/parser"
import {defineConfig, globalIgnores} from "eslint/config"

export default defineConfig([
    globalIgnores(["**/node_modules/*", "node/*", "playwright-report/*", "test-results/*", "coverage/*"]),
    ...pluginVue.configs["flat/base"],
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
