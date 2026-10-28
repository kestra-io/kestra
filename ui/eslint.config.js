// For more info, see https://github.com/storybookjs/eslint-plugin-storybook#configuration-flat-config-format
import storybook from "eslint-plugin-storybook"

import css from "@eslint/css"
import pluginVue from "eslint-plugin-vue"
import tsParser from "@typescript-eslint/parser"
import {defineConfig, globalIgnores} from "eslint/config"
import kestraTokens from "./scripts/tokens/eslintPlugin.mjs"
import kestraI18n from "./scripts/translations/eslintPlugin.mjs"
import {vueWithStyles} from "./scripts/css-lint/vueStyleProcessor.mjs"

export default defineConfig([
    globalIgnores(["**/node_modules/*", "node/*", "playwright-report/*", "test-results/*", "coverage/*", "**/dist/*", "**/storybook-static/*", "packages/kestra-sdk/src/openapi/*"]),
    ...pluginVue.configs["flat/base"],
    {
        files: ["**/*.vue"],
        processor: vueWithStyles(pluginVue.processors.vue),
    },
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
    // Here it is the tokens written in JavaScript; stylesheets get the CSS rule further down.
    // `.jsx`/`.tsx` carry no other rule in this repo, so they get the parser they need and this rule
    // alone — three Storybook stories were reaching for an undeclared token where nothing looked.
    {
        files: ["**/*.{js,mjs,cjs,ts,vue}"],
        plugins: {"kestra-tokens": kestraTokens},
        rules: {"kestra-tokens/no-undeclared-ks-token": "error"},
    },
    {
        files: ["**/*.{jsx,tsx}"],
        languageOptions: {parser: tsParser, parserOptions: {ecmaFeatures: {jsx: true}}},
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
        plugins: {"kestra-i18n": kestraI18n},
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
            "kestra-i18n/i18n-t-scope": "error",
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
        files: ["src/**/*.{ts,vue}", "packages/**/*.{ts,vue}"],
        rules: {
            "no-restricted-imports": ["error", {paths: [{
                name: "axios",
                message: "Call the Kestra API through the SDK or useClient(), and api.kestra.io through src/utils/publicApi.",
            }]}],
        },
    },
    ...storybook.configs["flat/recommended"],
    // `.css` files, plus the plain `<style>` blocks and `style="…"` attributes of each SFC.
    {
        files: ["**/*.css"],
        plugins: {css, "kestra-tokens": kestraTokens},
        language: "css/css",
        languageOptions: {tolerant: true},
        rules: {
            ...css.configs.recommended.rules,
            // Existence is the token rule's job; this one cannot see a variable declared in another file.
            "css/no-invalid-properties": ["error", {allowUnknownVariables: true}],
            // Most of the 71 are deliberate wins over Element Plus specificity, a cleanup of its own.
            "css/no-important": "off",
            "css/use-baseline": "warn",
            "kestra-tokens/no-undeclared-css-token": "error",
        },
    },
])
