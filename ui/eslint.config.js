import globals from "globals";
import pluginJs from "@eslint/js";
import tseslint from "typescript-eslint";
import pluginVue from "eslint-plugin-vue";

/** @type {import('eslint').Linter.Config[]} */
export default [
    {
        files: ["**/*.{js,mjs,cjs,ts,vue}"],
    },
    {languageOptions: {globals: globals.browser}},
    pluginJs.configs.recommended,
    ...tseslint.configs.recommended,
     {
        files: ["**/*.spec.js", "**/*.spec.ts", "vite.config.js", "vitest.config.js"],
        languageOptions: {globals: globals.node},
    },
    ...pluginVue.configs["flat/strongly-recommended"],
    {
        files: ["**/*.vue"],
        languageOptions: {parserOptions: {parser: tseslint.parser}},
        rules: {
            "vue/this-in-template": ["error"],
            "vue/html-indent": [
                "error",
                4,
                {
                    baseIndent: 1,
                },
            ],
            "vue/script-indent": [
                "error",
                4,
                {
                    baseIndent: 1,
                },
            ],
            "vue/max-attributes-per-line": [
                "error",
                {
                    singleline: 7,
                },
            ],
            "vue/multi-word-component-names": ["off"],
            "vue/no-deprecated-router-link-tag-prop": "off",
            "vue/object-curly-spacing": ["error", "never"],
            "vue/block-order": [
                "error",
                {
                    order: ["template", "script", "style"],
                },
            ],
        },
    },
    {
        rules: {
            quotes: ["error", "double"],
            "object-curly-spacing": ["error", "never"],
            "no-unused-vars": "off",
            "@typescript-eslint/no-unused-vars": [
                "error",
                {
                    // vars prefixed with '_' are ignored
                    argsIgnorePattern: "^_",
                },
            ],
            "@typescript-eslint/no-this-alias": "off",
            "@typescript-eslint/no-explicit-any": "off"
        }
    }
];
