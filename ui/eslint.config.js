import pluginVue from "eslint-plugin-vue"
import tsParser from "@typescript-eslint/parser"
import {defineConfig, globalIgnores} from "eslint/config"
import kestraTestHygiene from "./eslint-rules/index.js"

export default defineConfig([
    globalIgnores(["**/node_modules/*", "node/*", "playwright-report/*", "test-results/*", "coverage/*", "**/dist/*", "packages/kestra-sdk/src/openapi/*"]),
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
    {
        // Unit specs share one jsdom per worker (isolate: false), so state a spec
        // mutates and never restores breaks a later, unrelated file.
        files: ["**/*.spec.{js,ts}", "**/*.test.{js,ts}", "tests/unit/**/*.{js,ts}"],
        ignores: ["tests/e2e/**"],
        plugins: {"kestra-test-hygiene": kestraTestHygiene},
        rules: {
            "kestra-test-hygiene/no-direct-global-assignment": "error",
            "kestra-test-hygiene/no-unrestored-fake-timers": "error",
            "kestra-test-hygiene/no-unrestored-global-stub": "error",
            "kestra-test-hygiene/require-mock-reset": "error",
        },
    },

])
