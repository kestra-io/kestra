// @ts-check
import globals from "globals";
import pluginJs from "@eslint/js";
import {defineConfig, globalIgnores} from "eslint/config";
import tseslint from "typescript-eslint";
import pluginVue from "eslint-plugin-vue";
import {dirname} from "path";
import {fileURLToPath} from "url";

// resolve current directory for tsconfig
const __dirname = dirname(fileURLToPath(import.meta.url));

/** @type {import('eslint').Linter.Config[]} */
export default defineConfig([
    globalIgnores(["node_modules/*", "dist/*", "coverage/*"]),
    {languageOptions: {globals: globals.browser}},
    pluginJs.configs.recommended,
    ...tseslint.configs.recommended,
    {
        files: [
            "**/*.spec.js",
            "**/*.spec.ts",
            "vite.config.ts",
            "vitest.config.ts",
            "vitest.unit.config.ts",
            "vitest.config.*.ts",
            ".storybook/vitest.setup.ts",
        ],
        languageOptions: {globals: globals.node},
    },
    ...pluginVue.configs["flat/strongly-recommended"],
    {
        files: ["**/*.vue", "**/*.tsx", "**/*.jsx"],
        languageOptions: {parserOptions: {
            parser: tseslint.parser,
            tsConfigRootDir: __dirname,
        }},
        rules: {
            "vue/block-lang": ["warn",
                {
                    "script": {
                        "lang": "ts"
                    }
                }
            ],
            "vue/this-in-template": "error",
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
            "vue/component-name-in-template-casing": [
                "error",
                "PascalCase",
                {
                    "registeredComponentsOnly": true,
                }
            ],
            "vue/attribute-hyphenation": [
                "error",
                "never"
            ],
            "@typescript-eslint/consistent-type-assertions": [
                "error",
                {
                    assertionStyle: "as"
                }
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
                    argsIgnorePattern: "^_",
                    varsIgnorePattern: "^_",
                },
            ],
            "@typescript-eslint/no-this-alias": "off",
            "@typescript-eslint/no-explicit-any": "off",
            "no-console": ["error", {allow: ["warn", "error"]}]
        },
    },
    {
        files: ["**/tests/**"],
        rules: {
            "no-console": ["off"]
        }
    }
]);
